"""Opaque two-member relay. Deploy behind HTTPS; only ciphertext is accepted/stored."""
import base64, hashlib, hmac, json, os, re, sqlite3, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
DB=os.environ.get('PAIR_DB','pairs.sqlite3')
LOCK=threading.Lock()
ID=re.compile(r'^[a-f0-9]{32}$')
LIMIT=262144
TTL=7*86400
RATE={}

def connect():
    c=sqlite3.connect(DB)
    c.execute('CREATE TABLE IF NOT EXISTS rooms(id TEXT PRIMARY KEY, auth TEXT NOT NULL, updated REAL NOT NULL)')
    c.execute('PRAGMA secure_delete=ON')
    c.execute('CREATE TABLE IF NOT EXISTS revoked(id TEXT PRIMARY KEY, auth TEXT NOT NULL)')
    c.execute('CREATE TABLE IF NOT EXISTS writers(room TEXT, actor TEXT, auth TEXT NOT NULL, PRIMARY KEY(room,actor))')
    c.execute('CREATE TABLE IF NOT EXISTS slots(room TEXT, actor TEXT, payload TEXT, updated REAL, PRIMARY KEY(room,actor))')
    return c

class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup();self.connection.settimeout(15)
    def log_message(self,*args): pass
    def send(self,status,data):
        b=json.dumps(data).encode();self.send_response(status)
        self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(b)))
        self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(b)
    def handle_request(self):
        path=self.path.split('?')[0].strip('/').split('/')
        if len(path)!=2 or path[0]!='rooms' or not ID.fullmatch(path[1]):return self.send(404,{})
        peer=self.client_address[0];minute=int(time.time()/60)
        with LOCK:
            if len(RATE)>10000:RATE.clear()
            old,n=RATE.get(peer,(minute,0));n=n+1 if old==minute else 1;RATE[peer]=(minute,n)
        if n>120:return self.send(429,{})
        room=path[1];token=self.headers.get('Authorization','')
        if not re.fullmatch(r'Bearer [a-f0-9]{64}',token):return self.send(401,{})
        digest=hashlib.sha256(token.encode()).hexdigest();now=time.time()
        if self.command=='PUT':
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=LIMIT:return self.send(413,{})
                data=json.loads(self.rfile.read(size));actor=data['actor'];payload=data['payload']
                if not ID.fullmatch(actor) or not isinstance(payload,str) or len(payload)>200000 or not re.fullmatch(r'[A-Za-z0-9_-]+={0,2}',payload) or len(base64.urlsafe_b64decode(payload))<28:return self.send(400,{})
            except (ValueError,KeyError,TypeError):return self.send(400,{})

        with LOCK,connect() as db:
            expired=[r[0] for r in db.execute('SELECT id FROM rooms WHERE updated<?',(now-TTL,))]
            for old in expired:
                db.execute('DELETE FROM slots WHERE room=?',(old,))
                db.execute('DELETE FROM writers WHERE room=?',(old,))
            db.execute('DELETE FROM rooms WHERE updated<?',(now-TTL,))
            revoked=db.execute('SELECT auth FROM revoked WHERE id=?',(room,)).fetchone()
            if revoked:return self.send(410 if hmac.compare_digest(revoked[0],digest) else 403,{})
            found=db.execute('SELECT auth FROM rooms WHERE id=?',(room,)).fetchone()
            if found and not hmac.compare_digest(found[0],digest):return self.send(403,{})
            if self.command=='DELETE':
                if not found:return self.send(404,{})
                db.execute('INSERT OR REPLACE INTO revoked VALUES(?,?)',(room,digest));db.execute('DELETE FROM writers WHERE room=?',(room,));db.execute('DELETE FROM slots WHERE room=?',(room,));db.execute('DELETE FROM rooms WHERE id=?',(room,));db.commit();return self.send(200,{})
            if self.command=='GET':
                if not found:return self.send(404,{})
                return self.send(200,{'slots':[{'actor':a,'payload':p,'updated':int(t*1000)} for a,p,t in db.execute('SELECT actor,payload,updated FROM slots WHERE room=?',(room,))]})
            if self.command!='PUT':return self.send(405,{})
            writer=self.headers.get('X-Actor-Key','')
            if not re.fullmatch(r'[a-f0-9]{64}',writer):return self.send(401,{})
            whash=hashlib.sha256(writer.encode()).hexdigest()
            known=db.execute('SELECT auth FROM writers WHERE room=? AND actor=?',(room,actor)).fetchone()
            if known and not hmac.compare_digest(known[0],whash):return self.send(403,{})
            if not found:
                if db.execute('SELECT COUNT(*) FROM rooms').fetchone()[0]>=10000:return self.send(503,{})
                db.execute('INSERT INTO rooms VALUES(?,?,?)',(room,digest,now))
            actors=[r[0] for r in db.execute('SELECT actor FROM slots WHERE room=?',(room,))]
            if actor not in actors and len(actors)>=2:return self.send(409,{})
            if not known:db.execute('INSERT INTO writers VALUES(?,?,?)',(room,actor,whash))
            db.execute('INSERT OR REPLACE INTO slots VALUES(?,?,?,?)',(room,actor,payload,now))
            db.execute('UPDATE rooms SET updated=? WHERE id=?',(now,room));db.commit();return self.send(200,{})
    def do_GET(self):self.handle_request()
    def do_PUT(self):self.handle_request()
    def do_DELETE(self):self.handle_request()

if __name__=='__main__':
    os.umask(0o077)
    server=ThreadingHTTPServer(('0.0.0.0',8080),Handler)
    if os.environ.get('RELAY_TLS_CERT'):
        import ssl
        tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(os.environ['RELAY_TLS_CERT'],os.environ['RELAY_TLS_KEY'])
        server.socket=tls.wrap_socket(server.socket,server_side=True)
    server.serve_forever()
