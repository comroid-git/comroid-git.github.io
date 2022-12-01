<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%--@elvariable id="account" type="org.comroid.auth.entity.UserAccount"--%>
<html>
<head>
    <title>View Books</title>
    <link href="<c:url value="/style.css"/>" rel="stylesheet" type="text/css">
</head>
<body>
<c:if test="!loggedIn">
    <h3 class="error-text">You are not logged in</h3>
</c:if>
<c:otherwise>
    <p> Account ID:
        <span>${account.id}</span>
    </p>
    <p> Username:
        <span>${account.username}</span>
    </p>
    <p> E-Mail:
        <span>${account.email}</span>
    </p>
    <c:if test="!widget">
        <a href="~/change-password">Change Password</a>
    </c:if>
    <c:if test="(account.permit & 4) != 0">
        <br/>
        <br/>
        <a href="<c:url value="/admin/"/>">Open Administration Panel</a>
    </c:if>
</c:otherwise>
</body>
</html>