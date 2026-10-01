package org.wifimotion.test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class Pairing {
 public static String encode(String json){return "WG1:"+Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));}
 public static String decode(String raw){return raw.startsWith("WG1:")?new String(Base64.getUrlDecoder().decode(raw.substring(4)),StandardCharsets.UTF_8):raw;}
}
