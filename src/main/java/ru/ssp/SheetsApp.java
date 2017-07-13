package ru.ssp;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.rcarz.jiraclient.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 * Изменение ячеек чеклиста (коментарий, тестировщик, дата, ...)
 * - Редирект на страницу созданного бага Jira
 */


/* TODO try Catch Exceprions*/

@WebServlet("/sheetsapp")
public class SheetsApp extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control","no-cache");

        PrintWriter out = resp.getWriter();

        String access_token = req.getParameter("access_token");
        Sheets service = null;
        try {
            service = buildSheet(access_token);
        } catch (GeneralSecurityException e) {
            e.getMessage();
            return;
        }
        String testerMail = getMail(access_token);

        String issue = req.getParameter("issue");
        String row = req.getParameter("row");
        String sheetId = req.getParameter("sheetid");
        String spreadSheetId = req.getParameter("spreadsheetid");

        try { updatetReporter(spreadSheetId,service,testerMail,issue); } catch (Exception ignored) {}

        String comment = "https://jira.sib-soft.ru/jira/browse/" + issue;
        String commentCell = getCell(spreadSheetId,sheetId,service,"comment") + row;//"G" + row;

        String environment = null;
        try {
            environment = (String) setJiraClient().getIssue(issue).getField("environment");
        } catch (JiraException e) {
            e.getMessage();
        }
        String environmentCell = getCell(spreadSheetId,sheetId,service,"environment") + row;//"K" + row;

        String date = new SimpleDateFormat("dd.MM.yyyy").format(Calendar.getInstance().getTime());
        String dateCell = getCell(spreadSheetId,sheetId,service,"date") + row;//"L" + row;

        String tester = getTester(spreadSheetId,service,testerMail);
        String testerCell = getCell(spreadSheetId,sheetId,service,"tester") + row;

        updateCell(spreadSheetId, sheetId, commentCell, service, comment);
        updateLists(spreadSheetId, "D", service, environment);
        updateCell(spreadSheetId, sheetId, environmentCell, service, environment);
        updateLists(spreadSheetId, "F", service, date);
        updateCell(spreadSheetId, sheetId, dateCell, service, date);
        updateCell(spreadSheetId,sheetId,testerCell,service,tester);

        resp.sendRedirect(comment);
    }

    /**
     * Получает адрес ячейки, в которую необходимо внести изменения (коментарий, тестировщик, дата, ...)
     */
    protected String getCell(String spreadsheetId, String sheetId, Sheets service, String type) throws IOException
    {
        String row = "",column = "";
        switch (type)
        {
            case "comment": column = "B";
                break;
            case "tester": column = "C";
                break;
            case "environment" : column = "D";
                break;
            case "date": column = "E";
                break;
        }
        if (sheetId.indexOf("ЭФ_") == 0)
        {
            row = "2";
        }else if (sheetId.indexOf("ВИ_") == 0)
        {
            row = "3";
        }

        String range = "'Conf'!"+column+row;
        ValueRange resp = service.spreadsheets().values().get(spreadsheetId,range).execute();
        return (String)resp.getValues().get(0).get(0);
    }

    /**
     * Получает email адрес тестировщика
     * */
    protected String getMail(String access_token) throws IOException
    {
        String url = "https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=" + access_token;
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);

        CloseableHttpResponse response = httpClient.execute(httpGet);

        BufferedReader in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

        String jsonResponse = "";
        String line;
        while ((line = in.readLine()) != null) {
            jsonResponse+=line;
        }
        JsonParser parser = new JsonParser();
        JsonElement element = new JsonObject();
        try {
            element = parser.parse(jsonResponse);
        } catch (JsonParseException e)
        {
            return "-1";
        }
        JsonObject rootObject = element.getAsJsonObject();
        String email;
        try {
            email = rootObject.get("email").getAsString();
        } catch (NullPointerException e)
        {
            return "-1";
        }
        return email;
    }

    /**
     * Получает Фамилию тестировщика из листа "Списки"
     */
    protected String getTester(String spreadsheetId, Sheets service,String email) throws IOException
    {
        String tester = "";
        for (int i = 1; i < 30; i++) {
            String range = "'Списки'!C"+i;
            List<List<Object>> values = service.spreadsheets().values().get(spreadsheetId,range).execute().getValues();
            if (values != null) {
                String emails = (String) values.get(0).get(0);
                if (emails.equals(email)) {
                    range = "'Списки'!B" + i;
                    tester = (String) service.spreadsheets().values().get(spreadsheetId, range).execute().getValues().get(0).get(0);
                    break;
                }
            }
        }
        return tester;
    }

    /**
     * Получает Фамилию тестировщика из листа "Списки"
     */
    protected void updatetReporter(String spreadsheetId, Sheets service,String email, String issue) throws IOException, JiraException
    {
        String reporter = "";
        for (int i = 1; i < 30; i++) {
            String range = "'Списки'!C"+i;
            List<List<Object>> values = service.spreadsheets().values().get(spreadsheetId,range).execute().getValues();
            if (values != null) {
                String emails = (String) values.get(0).get(0);
                if (emails.equals(email)) {
                    range = "'Списки'!I" + i;
                    reporter = (String) service.spreadsheets().values().get(spreadsheetId, range).execute().getValues().get(0).get(0);
                    break;
                }
            }
        }
        User user = User.get(setJiraClient().getRestClient(),reporter);
        setJiraClient().getIssue(issue).update().field(Field.REPORTER,user).execute();
    }

    /**
     * устанавливает соединение с jira
     */
    protected JiraClient setJiraClient() {
        BasicCredentials credentials = new BasicCredentials("ExtJiraIntegration", "herExtJira");
        return new JiraClient("https://jira.sib-soft.ru:443/jira",credentials);
    }

    /**
     * Возращает экземляр класса Sheets
     */
    protected Sheets buildSheet (String access_token) throws GeneralSecurityException, IOException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(access_token);
        HttpTransport httpTransport = null;
        httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(httpTransport,JacksonFactory.getDefaultInstance(),credential).setApplicationName("JiraAutomatization").build();
    }

    /**
     * Вносит изменения в лист "Списки" при необходимости
     */
    protected void updateLists(String spreadsheetId, String column, Sheets service,String data) throws IOException
    {
        if (StringUtils.isBlank(data))
        {
            return;
        }
        String range = "'Списки'!" + column + ":" + column;
        ValueRange resp = service.spreadsheets().values().get(spreadsheetId,range).execute();
        resp.setMajorDimension("ROWS");
        List<List<Object>> values = resp.getValues();
        for (List<Object> val:
                values) {
            if (!val.isEmpty() && val != null)
            {
                if (data.equals((String)val.get(0)))
                    return;
            }
        }
        int i = 1;
        range = "'Списки'!" + column + i;
        resp = service.spreadsheets().values().get(spreadsheetId,range).execute();
        resp.setMajorDimension("ROWS");
        values = resp.getValues();
        while (values != null) {
            i++;
            range = "'Списки'!" + column + i;
            resp = service.spreadsheets().values().get(spreadsheetId,range).execute();
            resp.setMajorDimension("ROWS");
            values = resp.getValues();
        }
        String newRange = "'Списки'!" + column + i;
        ValueRange newResp = service.spreadsheets().values().get(spreadsheetId, newRange).execute();
        List<List<Object>> newValues = new ArrayList<>();
        List<Object> value = new ArrayList<>();
        value.add(0, data);
        newValues.add(0, value);
        newResp.setValues(newValues);
        BatchUpdateValuesRequest batchRequest = new BatchUpdateValuesRequest();
        batchRequest.setValueInputOption("RAW");

        List<ValueRange> updateValueRangeList = new ArrayList<>();
        updateValueRangeList.add(newResp);

        batchRequest.setData(updateValueRangeList);

        BatchUpdateValuesResponse updateResponse = service.spreadsheets().
                values().batchUpdate(spreadsheetId, batchRequest).
                execute();
    }

    /**
     * Вносит изменения в ячеки в текущем листе (коментарий, тестировщик, дата, ...)
     */
    protected void updateCell(String spreadsheetId, String sheetId, String cell, Sheets service,String data) throws IOException {
        if (StringUtils.isBlank(data))
        {
            return;
        }
        String range =  "'" + sheetId + "'!" + cell;
        ValueRange resp = new ValueRange();
        resp.setRange(range);
        List<List<Object>> values = new ArrayList<>();
        List<Object> value = new ArrayList<>();
        value.add(0,data);
        values.add(0, value);
        resp.setValues(values);

        BatchUpdateValuesRequest batchRequest = new BatchUpdateValuesRequest();
        batchRequest.setValueInputOption("RAW");

        List<ValueRange> updateValueRangeList = new ArrayList<>();
        updateValueRangeList.add(resp);

        batchRequest.setData(updateValueRangeList);

        BatchUpdateValuesResponse updateResponse = service.spreadsheets().
                values().batchUpdate(spreadsheetId, batchRequest).
                execute();
    }
}
