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

def test_outsider_access():
    jar_outsider = http.cookiejar.CookieJar()
    opener_outsider = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar_outsider), NoRedirectHandler())

    # 1. Register Outsider Student
    csrf, _ = get_csrf(opener_outsider, "http://localhost:8080/register")
    reg_data = {
        "role": "ROLE_STUDENT",
        "name": "외부학생",
        "studentNo": "20267777",
        "email": "outsider777@univ.ac.kr",
        "password": "password123",
        "passwordConfirm": "password123",
        "_csrf": csrf
    }
    post_data(opener_outsider, "http://localhost:8080/register", reg_data)

    # 2. Login as Outsider
    csrf, _ = get_csrf(opener_outsider, "http://localhost:8080/login")
    post_data(opener_outsider, "http://localhost:8080/login", {
        "email": "outsider777@univ.ac.kr",
        "password": "password123",
        "_csrf": csrf
    })

    # 3. Access Class 1 directly without enrollment
    req = urllib.request.Request("http://localhost:8080/classes/1")
    try:
        res = opener_outsider.open(req)
        html = res.read().decode('utf-8')
        print(f"[SECURITY CHECK] Status: {res.status}")
        if "학생 참여 초대 코드" in html:
            print("[VULNERABILITY DETECTED] Outsider can see the classroom page AND the invite code!")
        else:
            print("[SAFE] Outsider cannot see classroom")
    except urllib.error.HTTPError as e:
        print(f"[SECURITY CHECK] Outsider was blocked with HTTP {e.code}")

if __name__ == "__main__":
    test_outsider_access()
