#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# TODO: verify required environment variables are set
# TODO: emit comment block

echo "upstream targets-${groupName} {"

# can't have an empty 'upstream' block, so add a placeholder 'server' entry if needed
[[ -z "${serverAddresses}" ]] && echo "  server 0.0.0.0;"

for s in ${serverAddresses}; do
  echo "  server $s;"
done

echo "}"
