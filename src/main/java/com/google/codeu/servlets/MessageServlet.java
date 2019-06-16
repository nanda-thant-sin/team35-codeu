/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.codeu.servlets;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.codeu.data.Datastore;
import com.google.codeu.data.UserLocation;
import com.google.codeu.data.Message;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;

import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.ipinfo.api.IPInfo;
import io.ipinfo.api.errors.RateLimitedException;
import io.ipinfo.api.model.IPResponse;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;



/** Handles fetching and saving {@link Message} instances. */
@WebServlet("/messages")
public class MessageServlet extends HttpServlet {

  private Datastore datastore;

  @Override
  public void init() {
    datastore = new Datastore();
  }

  private String insertMediaTag(String content) {

    String regex = "((?:!\\[.*])https?://\\S+\\.(png|jpg|gif))";
    String replacement =  "<img src=\"$1\" alt=\"$1\" >";
    String newContent = content.replaceAll(regex, replacement);

    regex = "!\\[(.*)]\\((https?://\\S+\\.(png|jpg|gif))\\)";
    replacement = "<figure> <img src=\"$2\" alt=\"$2\">"
            + "<figcaption> $1 </figcatption>" + "<figure>";
    newContent = newContent.replaceAll(regex, replacement);

    regex = "(https?://\\S+\\.(mp4|webm|ogg))";
    replacement = "<video controls> <source src=\"$1\"> </video>";
    newContent = newContent.replaceAll(regex, replacement);

    regex = "(https?://\\S+\\.(mp3|wav|ogg))";
    replacement = "<audio controls> <source src=\"$1\"> </audio>";
    newContent = newContent.replaceAll(regex, replacement);

    return newContent;
  }

  /**
   * Responds with a JSON representation of {@link Message} data for a specific
   * user. Responds with an empty array if the user is not provided.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

    response.setContentType("application/json");

    String user = request.getParameter("user");

    if (user == null || user.equals("")) {
      // Request is invalid, return empty array
      response.getWriter().println("[]");
      return;
    }

    List<Message> messages = datastore.getMessages(user);
    Gson gson = new Gson();
    String json = gson.toJson(messages);

    response.getWriter().println(json);
  }

  /** Stores a new {@link Message}. */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

    UserService userService = UserServiceFactory.getUserService();
    if (!userService.isUserLoggedIn()) {
      response.sendRedirect("/index.html");
      return;
    }

    String user = userService.getCurrentUser().getEmail();
    String userEnteredContent = request.getParameter("text");

    Whitelist whitelist = Whitelist.relaxed();
    String sanitizedContent = Jsoup.clean(userEnteredContent, whitelist);

    String textWithMedia = insertMediaTag(sanitizedContent);

    Message message = new Message(user, textWithMedia);
    datastore.storeMessage(message);

    // store a userLocation here
    IPInfo ipInfo = IPInfo.builder().setToken("5099035df7d924").setCountryFile(new File("iptoaddress.json")).build();
    String ipAddress = request.getRemoteAddr();
    try {
      IPResponse ipResponse = ipInfo.lookupIP(ipAddress);
      // Print out the hostname
      System.out.println("Testing starts here");
      System.out.println(ipAddress);
      System.out.println(ipResponse);
      System.out.println(ipResponse.getCountryName());
      System.out.println(ipResponse.getCountryCode());
      System.out.println(ipResponse.getHostname());

      // TODO: Get the country corresponding to the country code in the json file -> OPTIONAL BUT WOULD BE BETTER
      UserLocation userLocation = new UserLocation(user, ipResponse.getCountryCode());
      datastore.storeLocation(userLocation);

    } catch (RateLimitedException ex) {
      System.out.println("Exceed rate limit");
    }

    response.sendRedirect("/user-page.html?user=" + user);
  }
}
