package ru.ssp;

import com.google.api.client.auth.oauth2.AuthorizationCodeTokenRequest;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
/*
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
*/

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Авторизация Google Api, получения кода авторизации
 * - Редирект на страницу изменения ячеек чеклиста
 */

@WebServlet("/gettoken")
public class GetToken extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control","no-cache");

        PrintWriter out = resp.getWriter();

        String code = req.getParameter("code");

        String state = req.getParameter("state");
        String[] splitedState = StringUtils.split(state,'/');

        String issue = splitedState[0];
        String spreadSheetId = splitedState[1];
        String sheetId = URLEncoder.encode(splitedState[2],"UTF-8");
        String row = splitedState[3];


        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost("https://www.googleapis.com/oauth2/v4/token");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("code", code));
        nvps.add(new BasicNameValuePair("client_id", "1023821904267-v4nrljdb608o8e29ke6jeo13l6a073ai.apps.googleusercontent.com"));
        nvps.add(new BasicNameValuePair("client_secret", "a2dAHzNxCvF_hByryh5c-J7P"));
        nvps.add(new BasicNameValuePair("redirect_uri", "http://jiraauto.tk/jira/gettoken"));
        nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));

        httpPost.setEntity(new UrlEncodedFormEntity(nvps));


        CloseableHttpResponse response = httpClient.execute(httpPost);

        BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        String line;
        String answer = "";
        while ((line = in.readLine()) != null)
        {
            answer+=line;
        }

        response.close();

        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(answer);
        JsonObject rootObject = element.getAsJsonObject();
        String access_token = rootObject.get("access_token").getAsString();

        String url = "http://jiraauto.tk/jira/sheetsapp?access_token="+access_token
                +"&issue="+issue
                +"&spreadsheetid="+spreadSheetId
                +"&sheetid="+sheetId
                +"&row="+row;

        resp.sendRedirect(url);


    }
}
