package com.fongmi.android.tv.api;

import static org.chromium.base.ThreadUtils.runOnUiThread;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Decoder {

    public static String getJson(String url) throws Exception {
        String key = url.contains(";") ? url.split(";")[2] : "";
        url = url.contains(";") ? url.split(";")[0] : url;
        String data = getData(url);
        if (data.isEmpty()) throw new Exception();
        if (Json.valid(data)) return fix(url, data);
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data);
        if (key.length() > 0) data = ecb(data, key);
        return fix(url, data);
    }

    private static String fix(String url, String data) {
        if (url.startsWith("file") || url.startsWith("clan") || url.startsWith("assets")) url = UrlUtil.convert(url);
        data = data.replace("./", url.substring(0, url.split("\\?")[0].lastIndexOf("/") + 1));
        return data;
    }

    public static String getExt(String ext) {
        try {
            return base64(getData(ext.substring(4)));
        } catch (Exception ignored) {
            return "";
        }
    }

    public static File getSpider(String url) {
        try {
            File file = Path.jar(url);
            String data = extract(getData(url.substring(4)));
            return data.isEmpty() ? file : Path.write(file, Base64.decode(data, Base64.DEFAULT));
        } catch (Exception ignored) {
            return Path.jar(url);
        }
    }

    private static String getData(String url) {
        if (url.startsWith("file")) return Path.read(url);
        if (url.startsWith("assets")) return Asset.read(url);
        HashMap<String, String> objectObjectHashMap = new HashMap<>();
        objectObjectHashMap.put("Referer",Settings.Secure.getString(App.get().getContentResolver(), Settings.Secure.ANDROID_ID));
        if (url.startsWith("http")) return OkHttp.string(url,objectObjectHashMap);
        return "";
    }


    private static String ecb(String data, String key) throws Exception {
        SecretKeySpec spec = new SecretKeySpec(padEnd(key).getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, spec);
        return new String(cipher.doFinal(Util.hex2byte(data)), StandardCharsets.UTF_8);
    }

    private static String cbc(String data) throws Exception {
        String decode = new String(Util.hex2byte(data)).toLowerCase();
        String key = padEnd(decode.substring(decode.indexOf("$#") + 2, decode.indexOf("#$")));
        String iv = padEnd(decode.substring(decode.length() - 13));
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        data = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        byte[] decryptData = cipher.doFinal(Util.hex2byte(data));
        return new String(decryptData, StandardCharsets.UTF_8);
    }

    private static String base64(String data) {
        String extract = extract(data);
        if (extract.isEmpty()) return data;
        return new String(Base64.decode(extract, Base64.DEFAULT));
    }

    private static String extract(String data) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9]{8}\\*\\*").matcher(data);
        return matcher.find() ? data.substring(data.indexOf(matcher.group()) + 10) : "";
    }

    private static String padEnd(String key) {
        return key + "0000000000000000".substring(key.length());
    }
}
