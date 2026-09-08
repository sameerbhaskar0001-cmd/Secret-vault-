import urllib.request
import re

url = "https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoRuntime.html"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    html = urllib.request.urlopen(req).read().decode('utf-8')
    methods = re.findall(r'<a href="[^"]+">([^<]+)</a>', html)
    print("Methods found:")
    # print unique methods
    for m in set(methods):
        if 'memory' in m.lower() or 'trim' in m.lower():
            print(m)
except Exception as e:
    print(e)
