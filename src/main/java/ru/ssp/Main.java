package ru.ssp;
//
/**
 * Добавления бага в Jira
 * - Редирект на страницу получения кода авторизации
 */

import net.rcarz.jiraclient.*;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;


@WebServlet("/")
@MultipartConfig
public class Main extends HttpServlet{

    private JiraClient jiraClient;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {}

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control","no-cache");
        PrintWriter out = resp.getWriter();


        String description =  "+*Предусловия:*+\n" + req.getParameter("preconditions") + "\n\n"
                + "+*Шаги воспроизведения:*+\n" + req.getParameter("regeneration_steps") + "\n\n"
                + "+*Ожидаемый результат:*+\n" + req.getParameter("expected_result") + "\n\n"
                + "+*Фактический результат:*+\n" + req.getParameter("actual_result") + "\n\n"
                + "+*Код сценария:*+\n" + req.getParameter("scenario_code") + "\n\n"
                + "+*Ссылка на ЧТЗ:*+ " + "[" + req.getParameter("link_text") + "|" + req.getParameter("link_adress") + "]" + "\n\n"
                + "+*Версия ЧТЗ:*+ " + req.getParameter("TOR_version");

        HashMap<String,String> fields = new HashMap<>();
        fields.put("summary", req.getParameter("summary"));
        fields.put("priority", req.getParameter("priority"));
        fields.put("components", req.getParameter("components"));
        fields.put("fix_versions", req.getParameter("fix_versions"));
        fields.put("environment", req.getParameter("environment"));
        fields.put("description", description);
        fields.put("link_issue", req.getParameter("link_issue"));

        Part filePart1 = req.getPart("file1");
        Part filePart2 = req.getPart("file2");
        Part filePart3 = req.getPart("file3");

        ArrayList<File> files = new ArrayList<>();
        files.add(getFile(filePart1));
        files.add(getFile(filePart2));
        files.add(getFile(filePart3));


        setJiraClient();
        patchSSL();
        String issue = "";
        try {
            if (StringUtils.isBlank(req.getParameter("comment"))) {
                issue = createIssue(fields, files);
            } else {
                issue = updateIssue(req.getParameter("comment"), fields, files);
            }
        } catch (JiraException e)
        {
            out.print(e.getMessage());
            for (StackTraceElement element:
                 e.getStackTrace()) {
                out.print(element);
                out.print("<br>");
            }
            return;
        }

        String sheet = URLEncoder.encode(req.getParameter("sheet"),"UTF-8");
        out.print(sheet+"<br>"+ URLDecoder.decode(sheet,"UTF-8"));


        String url = "http://jiraauto.tk/jira/getcode?issue=" + issue + "&" +
                "sheet=" + sheet + "&" +
                "spreadsheet=" + req.getParameter("spreadsheet") + "&" +
                "row=" + req.getParameter("row");


        resp.sendRedirect(url);
    }

    /**
     * получает attachment из multipart/form-data отправленных через форму на сайте
     */
    private File getFile (Part filePart) throws IOException {
        String filename = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
        filename = Translit.toTranslit(filename);
        InputStream inputStream = filePart.getInputStream();
        File file = new File("/"+filename);
        FileOutputStream outputSteam = null;
        try {
            outputSteam = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
        int read = 0;
        byte[] bytes = new byte[1024];
        while ((read = inputStream.read(bytes)) != -1)
        {
            outputSteam.write(bytes,0,read);
        }
        outputSteam.close();
        return file;
    }

    /**
     * устанавливает соединение с jira
     */
    private void setJiraClient() {
        BasicCredentials credentials = new BasicCredentials("ExtJiraIntegration", "herExtJira");
        this.jiraClient = new JiraClient("https://jira.sib-soft.ru/jira",credentials);
    }

    /**
     * Отключает проверку подлинности SSL сертификата
     */
    private void patchSSL()
    {
        HttpClient httpClient;
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");

            // set up a TrustManager that trusts everything
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs,
                                               String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs,
                                               String authType) {
                }
            }}, new SecureRandom());

            SSLSocketFactory sf = new SSLSocketFactory(sslContext,SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            Scheme httpsScheme = new Scheme("https", 443, sf);
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(httpsScheme);

            ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);
            httpClient = new DefaultHttpClient(cm);
        } catch (Exception ignored) {
            return;
        }

        // Patch RestClient
        try {
            RestClient restClient = this.jiraClient.getRestClient();
            java.lang.reflect.Field httpClientField = restClient.getClass().getDeclaredField("httpClient");
            httpClientField.setAccessible(true);
            httpClientField.set(restClient, httpClient);
        } catch (Exception ignored) {
        }
    }

    /**
     * Создает баг в Jira
     */
    private String createIssue(Map<String,String> fields, List<File> files) throws JiraException {
                Issue issue = this.jiraClient.createIssue("HCS","Story (Ошибка)")
                .field(Field.SUMMARY, fields.get("summary"))
                .field(Field.PRIORITY,fields.get("priority"))
                .field("environment", fields.get("environment"))
                .field(Field.DESCRIPTION, fields.get("description"))
                .execute();

        updateSomeFields(fields.get("components"),fields.get("fix_versions"),issue);

        //String issueLink = StringUtils.substringAfterLast(fields.get("link_issue"),"/");
        issue.link(fields.get("link_issue"),"Dependent");

        for (File file :
                files) {
            try {
                issue.addAttachment(file);
                file.delete();
            } catch (Exception ignored){}
        }
        return issue.getKey();
    }

    /**
     * Обновляет существующий баг в Jira
     */
    private String updateIssue(String comment, Map<String,String> fields, List<File> files) throws JiraException {
        String issueKey = StringUtils.substringAfterLast(comment,"/");
        Issue issue = this.jiraClient.getIssue(issueKey);
        issue.update().field(Field.SUMMARY, fields.get("summary"))
                .field(Field.PRIORITY,fields.get("priority"))
                .field("environment", fields.get("environment"))
                .field(Field.DESCRIPTION, fields.get("description"))
                .execute();

        //updateSomeFields(fields.get("components"),fields.get("fix_versions"),issue);

        boolean existLink = false;
        for (IssueLink link:
                issue.getIssueLinks()) {
            if (link.getOutwardIssue().getKey().equals(fields.get("link_issue")) && link.getType().getName().equals("Dependent"))
            {
                existLink = true;
            }
        }
        if (!existLink)
        {
            issue.link(fields.get("link_issue"),"Dependent");
        }

        for (File file :
                files) {
            try {
                issue.addAttachment(file);
                file.delete();
            } catch (Exception ignored){}
        }
        return issue.getKey();
    }

    /**
     * Обновляет поля Components и Fix-Versions
     */
    private void updateSomeFields(String comps, String vers, Issue issue) throws JiraException
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("self","https://jira.sib-soft.ru/jira/rest/api/2/customFieldOption/12259");
        jsonObject.put("value","ГИС ЖКХ");
        jsonObject.put("id","12259");
        issue.update().field("customfield_11534", jsonObject).execute();

        ArrayList<Component> components = new ArrayList<>();
        for (Component component:
                jiraClient.getComponentsAllowedValues("HCS","Story (Тестирование)")) {
            if (component.getName().equals(comps))
            {
                components.add(component);
            }
        }
        if(!components.isEmpty())
        {
            issue.update().field(Field.COMPONENTS, components).execute();
        }

        ArrayList<Version> versions = new ArrayList<>();
        for (Version version :
                jiraClient.getProject("HCS").getVersions()) {
            if (version.getName().equals(vers))
            {
                versions.add(version);
            }
        }
        if (!versions.isEmpty())
        {
            issue.update().field(Field.FIX_VERSIONS, versions).execute();
        }
    }
}