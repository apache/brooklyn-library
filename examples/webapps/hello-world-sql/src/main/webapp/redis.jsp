<%@ page import="java.util.List" %>
<%@ page import="redis.clients.jedis.Jedis" %>

<html>
<!--
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
-->
<head>
  <title>Sample Application Database JSP Page</title>
</head>
<br/>
<body bgcolor=white>

<table border="0">
  <tr>
    <td align=center>
      <img src="images/bridge-small.png">
    </td>
    <td>
      <h1>Sample Brooklyn Deployed WebApp (Database JSP)</h1>
      This is the output of a JSP page that is part of the Hello, World application,
      deployed by Brooklyn, to show <b>Redis database interactivity</b>.
    </td>
  </tr>
</table>
<br/>
<p>Visitors:</p>
<ul>
<%
  String redisUrl=System.getProperty("brooklyn.example.redis.host");
  Jedis jedis = new Jedis(redisUrl);

  if (request.getParameter("name")!=null) {
      jedis.lpush("messages", request.getParameter("name")+":"+request.getParameter("message"));
  }

  List<String> messages = jedis.lrange("messages", 0, 10);
  for (int i =0; i < messages.size(); i++){
      String[] messageParts = messages.get(i).split(":");
      String name = messageParts[0];
      String message = messageParts[1];
    %>
    <li> <b><%= name %></b>: <%= message %> </li>
      <% } %>
</ul>

<br/>

<p>Please enter a message:</p>

<form action="redis.jsp" method="GET">
  <table>
    <tr><td>Name: </td><td><input type="text" name="name"></td></tr>
    <tr><td>Message: </td><td><input type="text" name="message"></td></tr>
  </table>
  <input type="submit" value="Submit"/>
</form>

<br/>
<p>Click <a href="index.html">here</a> to go back to the main page.</p>
</body>
</html>
