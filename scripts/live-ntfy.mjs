import WebSocket from 'ws';
import { randomBytes, randomUUID, createCipheriv, createDecipheriv } from 'node:crypto';
const key=randomBytes(32), commands='wg-test-c-'+randomBytes(24).toString('hex'), events='wg-test-e-'+randomBytes(24).toString('hex');
function seal(topic,data){const iv=randomBytes(12),c=createCipheriv('aes-256-gcm',key,iv);c.setAAD(Buffer.from(topic));return Buffer.concat([iv,c.update(JSON.stringify(data)),c.final(),c.getAuthTag()]).toString('base64');}
function open(topic,value){const b=Buffer.from(value,'base64'),c=createDecipheriv('aes-256-gcm',key,b.subarray(0,12));c.setAAD(Buffer.from(topic));c.setAuthTag(b.subarray(-16));return JSON.parse(Buffer.concat([c.update(b.subarray(12,-16)),c.final()]));}
const sockets=[];
async function subscribe(topic){const ws=new WebSocket('wss://ntfy.sh/'+topic+'/ws?since=5m');sockets.push(ws);const queue=[];let waiter=null;ws.on('message',v=>{const m=JSON.parse(v.toString());if(m.event==='message'){const data=open(topic,m.message);if(waiter){const f=waiter;waiter=null;f(data);}else queue.push(data);}});await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(new Error('Connect timeout')),15000);ws.once('open',()=>{clearTimeout(timeout);resolve();});ws.once('error',e=>{clearTimeout(timeout);reject(e);});});return ()=>queue.length?Promise.resolve(queue.shift()):new Promise((resolve,reject)=>{const timeout=setTimeout(()=>{waiter=null;reject(new Error('Receive timeout'));},15000);waiter=x=>{clearTimeout(timeout);resolve(x);};});}
async function publish(topic,data,cache){const response=await fetch('https://ntfy.sh/'+topic,{method:'POST',headers:{'Content-Type':'text/plain','X-Firebase':'no','X-Cache':cache?'yes':'no'},body:seal(topic,data),signal:AbortSignal.timeout(15000)});if(!response.ok)throw new Error('Publish HTTP '+response.status);}
try{
 const readCommand=await subscribe(commands),readEvent=await subscribe(events),request=randomUUID();
 await publish(commands,{kind:'command',action:'arm',request,id:randomUUID(),time:Date.now()},false);
 const command=await readCommand();if(command.request!==request||command.action!=='arm')throw new Error('Command mismatch');
 await publish(events,{kind:'status',armed:true,request,id:randomUUID(),time:Date.now()},true);
 const status=await readEvent();if(!status.armed||status.request!==request)throw new Error('Confirmation mismatch');
 await publish(events,{kind:'motion',id:randomUUID(),time:Date.now()},true);
 if((await readEvent()).kind!=='motion')throw new Error('Event mismatch');
 console.log('PASS: encrypted command, correlated confirmation, motion event over public ntfy WebSocket/HTTPS. Synthetic data only.');
}finally{for(const ws of sockets)ws.terminate();}
