import json
import socket
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

notes = []
HTTP_PORT = 8000
UDP_PORT = 8888

# 1. HTTP Server for receiving and serving notes
class NoteServer(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'application/json; charset=utf-8')
        self.end_headers()
        response_data = json.dumps(notes, ensure_ascii=False)
        self.wfile.write(response_data.encode('utf-8'))

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        request_body = self.rfile.read(content_length)
        new_note = json.loads(request_body.decode('utf-8'))
        notes.append(new_note)
        
        print(f"📥 New note received: {new_note}")
        
        self.send_response(201)
        self.send_header('Content-type', 'application/json; charset=utf-8')
        self.end_headers()
        self.wfile.write(json.dumps({"status": "ok"}).encode('utf-8'))

    def log_message(self, format, *args):
        # Silence default HTTP access logs to keep terminal clean
        return

# 2. UDP Discovery Service: listens for phone broadcasts on port 8888
def start_udp_discovery_server():
    udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    udp_socket.bind(('0.0.0.0', UDP_PORT))
    
    while True:
        try:
            data, addr = udp_socket.recvfrom(1024)
            message = data.decode('utf-8', errors='ignore').strip()
            
            if message == "DISCOVER_NOTES_SERVER":
                reply = f"NOTES_SERVER_ACK:{HTTP_PORT}"
                udp_socket.sendto(reply.encode('utf-8'), addr)
                print(f"📡 Auto-discovery: phone detected at {addr[0]}! Connected successfully.")
        except Exception as e:
            print(f"UDP Error: {e}")

# Start UDP discovery in a background thread
udp_thread = threading.Thread(target=start_udp_discovery_server, daemon=True)
udp_thread.start()

print(f"🚀 Notes server running on port {HTTP_PORT}...")
print(f"📡 UDP Auto-Discovery active on port {UDP_PORT} (waiting for phone broadcast)...")
print(f"View notes in your browser at: http://localhost:{HTTP_PORT}\n")

httpd = HTTPServer(('0.0.0.0', HTTP_PORT), NoteServer)
httpd.serve_forever()
