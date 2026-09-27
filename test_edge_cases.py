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

def get_csrf(opener, url):
    req = urllib.request.Request(url)
    try:
        response = opener.open(req)
        html = response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        html = e.read().decode('utf-8')
    parser = CSRFParser()
    parser.feed(html)
    return parser.csrf, html

def post_data(opener, url, data_dict):
    data = urllib.parse.urlencode(data_dict).encode('utf-8')
    req = urllib.request.Request(url, data=data)
    try:
        res = opener.open(req)
        return res.status, res.headers, res.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read().decode('utf-8')

class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def test_edge_cases():
    cookie_jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar), NoRedirectHandler())

    print("--- [Edge 1] Unauthenticated access to /classes/new ---")
    req = urllib.request.Request("http://localhost:8080/classes/new")
    try:
        res = opener.open(req)
        print("FAILED: /classes/new should not be accessible without login")
    except urllib.error.HTTPError as e:
        if e.code == 302 and "login" in e.headers.get("Location", ""):
            print("SUCCESS: Redirected to login as expected (302)")
        else:
            print(f"FAILED: Unexpected status {e.code}")

    print("--- [Edge 2] Duplicate email registration rejection ---")
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/register")
    dup_data = {
        "role": "ROLE_STUDENT",
        "name": "클론",
        "studentNo": "20269999",
        "email": "albert@univ.ac.kr", # Already registered in previous test
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf_token
    }
    code, headers, html = post_data(opener, "http://localhost:8080/register", dup_data)
    if "이미 가입된 이메일입니다" in html:
        print("SUCCESS: Duplicate email properly rejected with error message")
    else:
        print(f"FAILED: Did not find duplicate error message. Status: {code}")

    print("--- [Edge 3] Invalid invite code rejection ---")
    # Login as student
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/login")
    post_data(opener, "http://localhost:8080/login", {
        "email": "albert@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf_token
    })
    
    # Try bad invite code
    csrf_token, _ = get_csrf(opener, "http://localhost:8080/classes/join")
    code, headers, html = post_data(opener, "http://localhost:8080/classes/join", {
        "inviteCode": "XXXXXX",
        "_csrf": csrf_token
    })
    if "일치하는 수업을 찾을 수 없습니다" in html:
        print("SUCCESS: Invalid invite code properly rejected with error message")
    else:
        print(f"FAILED: Invalid invite code handling failed. Status: {code}")

    print("=== ALL EDGE CASE TESTS PASSED ===")

if __name__ == "__main__":
    test_edge_cases()
