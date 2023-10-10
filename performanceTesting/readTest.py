#!/usr/bin/python3

import random, string, subprocess

for i in range(10000):
	shortResource = ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(20))
	request="http://localhost:8080/" + shortResource
	print(request)
	subprocess.call(["curl", "-X", "GET", request], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
