import base64, json, os, tempfile, threading, unittest, urllib.request, urllib.error
from http.server import ThreadingHTTPServer
import relay
class RelayTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();relay.DB=os.path.join(self.tmp.name,'db');relay.RATE.clear()
        self.server=ThreadingHTTPServer(('127.0.0.1',0),relay.Handler)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.base=f'http://127.0.0.1:{self.server.server_port}/rooms/'+('a'*32)
        self.payload=base64.urlsafe_b64encode(os.urandom(80)).decode()
    def tearDown(self):self.server.shutdown();self.server.server_close();self.tmp.cleanup()
    def request(self,method,actor='1'*32,auth='a'*64,writer='b'*64):
        data=json.dumps({'actor':actor,'payload':self.payload}).encode() if method=='PUT' else None
        req=urllib.request.Request(self.base,data=data,method=method,headers={'Authorization':'Bearer '+auth,'X-Actor-Key':writer})
        try:
            with urllib.request.urlopen(req) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,{}
    def test_auth_membership_private_writers_and_revocation(self):
        self.assertEqual(200,self.request('PUT')[0])
        self.assertEqual(403,self.request('GET',auth='c'*64)[0])
        self.assertEqual(403,self.request('PUT',writer='d'*64)[0])
        self.assertEqual(200,self.request('PUT',actor='2'*32,writer='e'*64)[0])
        self.assertEqual(409,self.request('PUT',actor='3'*32)[0])
        status,data=self.request('GET');self.assertEqual(200,status);self.assertEqual(2,len(data['slots']))
        self.assertEqual(self.payload,data['slots'][0]['payload'])
        self.assertEqual(200,self.request('DELETE')[0])
        self.assertEqual(410,self.request('PUT')[0]);self.assertEqual(410,self.request('GET')[0])
if __name__=='__main__':unittest.main()
