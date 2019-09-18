# Copyright 2019 Sam Shen
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import botocore.loaders
import json
import textwrap

def wj(name, val):
    with open("src/main/resources/com/github/slshen/genaws/data/" + name, "w") as f:
        json.dump(val, f, indent=True)

loader = botocore.loaders.create_loader()
services = loader.list_available_services("service-2")
wj("services.json", services)

for service_name in services:
    service = loader.load_service_model(service_name, "service-2")
    wj(service_name + "-metadata.json", service['metadata'])
