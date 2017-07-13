package ru.ssp;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

/**
 * Авторизация Google Api, получения кода авторизации
 * - Редирект на страницу получения Access token(а)
 */

@WebServlet("/getcode")
public class GetCode extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control","no-cache");
        PrintWriter out = resp.getWriter();

        String issue = req.getParameter("issue");
        String sheet = URLEncoder.encode(req.getParameter("sheet"),"UTF-8");
        String spreadsheet = req.getParameter("spreadsheet");
        String row = req.getParameter("row");

        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "scope=" + "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/userinfo.email&" +
                "access_type=offline&" +
                "include_granted_scopes=true&" +
                "state=" + issue + "/" + spreadsheet + "/" + sheet  + "/" + row + " &"+
                "redirect_uri=" + "http://jiraauto.tk/jira/gettoken&" +
                "response_type=code&" +
                "client_id=" + "1023821904267-v4nrljdb608o8e29ke6jeo13l6a073ai.apps.googleusercontent.com";
        resp.sendRedirect(url);

    }
}
