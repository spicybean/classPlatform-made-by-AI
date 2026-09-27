import urllib.request
import urllib.parse
import http.cookiejar
from html.parser import HTMLParser

class CSRFParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.csrf = None
    def handle_starttag(self, tag, attrs):
        if tag == "input":
            attrs_dict = dict(attrs)
            if attrs_dict.get("name") == "_csrf":
                self.csrf = attrs_dict.get("value")

cookie_jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))
urllib.request.install_opener(opener)

# 1. Get page
req = urllib.request.Request("http://localhost:8080/register")
response = urllib.request.urlopen(req)
html = response.read().decode('utf-8')

parser = CSRFParser()
parser.feed(html)
csrf_token = parser.csrf

print("Found CSRF Token:", csrf_token)

# 2. Register POST
data = urllib.parse.urlencode({
    "role": "ROLE_STUDENT",
    "name": "홍길동",
    "studentNo": "20261234",
    "email": "teststudent2@univ.ac.kr",
    "password": "password123",
    "passwordConfirm": "password123",
    "_csrf": csrf_token
}).encode('utf-8')

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar), NoRedirectHandler())
urllib.request.install_opener(opener)

req = urllib.request.Request("http://localhost:8080/register", data=data)
try:
    response = urllib.request.urlopen(req)
    print("FAILED: Did not redirect")
except urllib.error.HTTPError as e:
    if e.code == 302 and "login" in e.headers.get("Location"):
        print("SUCCESS: Redirected to login page after registration.")
        
        # 3. Test Login
        login_data = urllib.parse.urlencode({
            "email": "teststudent2@univ.ac.kr",
            "password": "password123",
            "_csrf": csrf_token
        }).encode('utf-8')
        req = urllib.request.Request("http://localhost:8080/login", data=login_data)
        try:
            login_res = urllib.request.urlopen(req)
            print("FAILED: Login did not redirect")
        except urllib.error.HTTPError as e2:
            if e2.code == 302 and e2.headers.get("Location") == "http://localhost:8080/":
                print("SUCCESS: Login successful, redirected to home.")
            else:
                print("FAILED: Login did not redirect to home.")
    else:
        print("FAILED: Registration redirect to wrong URL:", e.headers.get("Location"))
