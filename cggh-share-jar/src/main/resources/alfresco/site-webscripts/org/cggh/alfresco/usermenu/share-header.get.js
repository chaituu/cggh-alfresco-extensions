var userMenuWidgets = 
  widgetUtils.findObject(model.jsonModel, "id", "HEADER_USER_MENU");
if (false && userMenuWidgets != null)
{
    //Would need to replace the LogoutService module as well
    //Also logout is now a POST which makes life more complicated
    userMenuWidgets.config.widgets.push({
        id: "HEADER_USER_MENU_LOGOUT",
        name: "alfresco/header/AlfMenuItem",
        config:
        {
           id: "HEADER_USER_MENU_LOGOUT",
           label: "logout.label",
           iconClass: "alf-user-logout-icon",
           publishTopic: "ALF_DOLOGOUT",
//           targetUrl:  "/dologout" + "?redirectURL=https://www.malariagen.net/sso/logout&redirectURLQueryKey=service&redirectURLQueryValue=https://www.malariagen.net/"
        }
     });

}



var changePasswordItem = 
	  widgetUtils.findObject(model.jsonModel, "id", "HEADER_USER_MENU_PASSWORD");
if (changePasswordItem != null)
{
	changePasswordItem.config.targetUrl = "../../pwm/private/ChangePassword";
}