<%@ page contentType="text/html; charset=utf-8" pageEncoding="UTF-8"%>
<%--
 Alfresco Report page
--%>

<%@ page import="java.util.Date" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Collection" %>

<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>

<%@ page import="org.alfresco.report.Report" %>
<%@ page import="org.alfresco.report.Helptext"%>
<%@ page import="org.alfresco.report.ReportGetter"%>

<%@ page import="org.apache.solr.common.util.NamedList" %>

<%!
private String getParam(HttpServletRequest req, String param)
{
    return req.getParameter(param) != null ? req.getParameter(param) : "";
}
%>

<html>
<head>
<% request.setCharacterEncoding("UTF-8"); %>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" type="text/css" href="../admin/solr-admin.css">
<link rel="icon" href="../admin/favicon.ico" type="image/ico"></link>
<link rel="shortcut icon" href="../admin/favicon.ico" type="image/ico"></link>
<title>Alfresco Report page</title>
</head>

<body>
<a href="/solr"><img border="0" align="right" height="78" width="142" src="../admin/solr_small.png" alt="Solr"></a>
<h1>Alfresco Report</h1>
<br/>
<br clear="all">

    <form name=queryForm method="GET" action="/solr/report/" accept-charset="UTF-8">
    <table>
        <tr><td><strong>Query parameters</strong></td><td colspan=2/></tr>
        <tr><td/><td><label for="fromTime">fromTime:</label></td><td><input id="fromTime" name="fromTime" type="text" value='<%= getParam(request, "fromTime") %>' /></td></tr>
        <tr><td/><td><label for="toTime">toTime:</label></td><td><input id="toTime" name="toTime" type="text" value='<%= getParam(request, "toTime") %>' /></td></tr>
        <tr><td/><td><label for="fromTx">fromTx:</label></td><td><input id="fromTx" name="fromTx" type="text" value='<%= getParam(request, "fromTx") %>' /></td></tr>
        <tr><td/><td><label for="toTx">toTx:</label></td><td><input id="toTx" name="toTx" type="text" value='<%= getParam(request, "toTx") %>' /></td></tr>
        <tr><td/><td><label for="fromAclTx">fromAclTx:</label></td><td><input id="fromAclTx" name="fromAclTx" type="text" value='<%= getParam(request, "fromAclTx") %>' /></td></tr>
        <tr><td/><td><label for="toAclTx">toAclTx:</label></td><td><input id="toAclTx" name="toAclTx" type="text" value='<%= getParam(request, "toAclTx") %>' /></td></tr>
        <tr><td/><td/><td><input class="stdbutton" type="submit" value="report" onclick="queryForm.submit()"></td></tr>
     <table>
    </form>
<br />

    <%
        Report report = ReportGetter.getReport(request);
        if (!report.isValid()) {
    %>
    <table>
        <tr>
            <td>Error loading report:</td>
            <td><%=report.getInfo().get("error")%></td>
        </tr>
    </table>
    <br />
    <%
        } else {
            Helptext helptext = new Helptext(request);
            Iterator itCores = report.getInfo().iterator();
            while (itCores.hasNext()) {
                Map.Entry entry = (Map.Entry) itCores.next();
                String core = (String) entry.getKey();
    %>
    <table>
        <tr>
            <td><h3>Core: <%=core%></h3></td>
            <td colspan=2/>
        </tr>
        <tr>
            <td><strong>Parameter</strong></td>
            <td><strong>Value</strong></td>
            <td><strong>Help text</strong></td>
        </tr>
        <%
            NamedList fields = (NamedList) entry.getValue();
                    Iterator itFields = fields.iterator();
                    while (itFields.hasNext()) {
                        Map.Entry field = (Map.Entry) itFields.next();
                        String key = (String) field.getKey();
        %>
        <tr>
            <td><%=key%></td>
            <td><%=field.getValue()%></td>
            <td><%=helptext.get(key)%></td>
        </tr>
        <%
            }
        %>
    </table>
    <br />
    <%
        }
        }
    %>

</body>
</html>