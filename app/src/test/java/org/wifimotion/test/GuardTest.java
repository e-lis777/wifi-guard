package org.wifimotion.test;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import java.util.Base64;
public class GuardTest {
 @Test public void pairingPreservesUnicode(){String config="{\"name\":\"Гостиная — комната 🏠\"}";String code=Pairing.encode(config);assertTrue(code.matches("WG1:[A-Za-z0-9_-]+"));assertEquals(config,Pairing.decode(code));assertEquals(config,Pairing.decode(config));}
 @Test public void stableNoiseAndRepeatedPasses(){Detector d=new Detector();d.mean=-30;d.sd=.3;for(int i=0;i<100;i++)assertFalse(d.sample(-30));assertFalse(d.sample(-35));assertFalse(d.sample(-30));assertFalse(d.sample(-33));assertTrue(d.sample(-34));for(int i=0;i<20;i++)assertFalse(d.sample(-35));assertFalse(d.sample(-30));assertFalse(d.sample(-34));assertTrue(d.sample(-34));}
 @Test public void noisyBackgroundRaisesThreshold(){Detector d=new Detector();d.mean=-50;d.sd=2;for(int i=0;i<20;i++)assertFalse(d.sample(-54));assertFalse(d.sample(-58));assertTrue(d.sample(-58));}
 @Test public void sensitivityChangesThreshold(){Detector d=new Detector();d.mean=-30;d.sd=0;d.sensitivity=2;assertFalse(d.sample(-32));assertTrue(d.sample(-32));d.reset();d.sensitivity=0;assertFalse(d.sample(-34));assertFalse(d.sample(-34));}
 @Test public void authenticatedAndChannelBound()throws Exception{String key=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);SecureMessage c=new SecureMessage(key,"events");String encrypted=c.seal(new JSONObject().put("kind","motion"));assertEquals("motion",c.open(encrypted).getString("kind"));try{new SecureMessage(key,"commands").open(encrypted);fail("Different direction accepted");}catch(javax.crypto.AEADBadTagException expected){}byte[] raw=Base64.getDecoder().decode(encrypted);raw[15]^=1;try{c.open(Base64.getEncoder().encodeToString(raw));fail("Tampered message accepted");}catch(javax.crypto.AEADBadTagException expected){}}
 @Test public void delayedCommandsExpire()throws Exception{long now=1000000;assertTrue(SecureMessage.timely(new JSONObject().put("time",now-20000),now,90000));assertFalse(SecureMessage.timely(new JSONObject().put("time",now-91000),now,90000));assertFalse(SecureMessage.timely(new JSONObject().put("time",now+31000),now,90000));assertFalse(SecureMessage.timely(new JSONObject(),now,90000));}
}
