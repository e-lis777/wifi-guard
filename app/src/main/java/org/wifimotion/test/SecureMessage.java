package org.wifimotion.test;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
public final class SecureMessage {
 final SecretKeySpec key;final String channel;
 public SecureMessage(String k,String channel){key=new SecretKeySpec(Base64.getUrlDecoder().decode(k),"AES");this.channel=channel;}
 public String seal(JSONObject data)throws Exception{byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(channel.getBytes(StandardCharsets.UTF_8));byte[] encrypted=c.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));byte[] all=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,all,0,12);System.arraycopy(encrypted,0,all,12,encrypted.length);return Base64.getEncoder().encodeToString(all);}
 public JSONObject open(String payload)throws Exception {if(payload.length()>4096)throw new Exception("Size");byte[] all=Base64.getDecoder().decode(payload);if(all.length<28)throw new Exception("Size");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,all,0,12));c.updateAAD(channel.getBytes(StandardCharsets.UTF_8));return new JSONObject(new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8));}
 public static boolean timely(JSONObject m,long now,long age){long time=m.optLong("time",0);return time>0&&time<=now+30000&&now-time<=age;}
}
