package org.vvcephei.banketl.etl

import java.util

import com.sun.jersey.api.client.filter.ClientFilter
import com.sun.jersey.api.client.{Client, ClientRequest, ClientResponse}
import com.sun.jersey.core.util.MultivaluedMapImpl
import org.htmlcleaner.{HtmlCleaner, TagNode}

import scala.collection.JavaConversions._

object EtlWeb {
  def main(args: Array[String]): Unit = {
    val client = new Client()

    // add a filter to set cookies received from the server and to check if login has been triggered
    client.addFilter(new ClientFilter {
      val cookies = new util.ArrayList[AnyRef]()

      override def handle(cr: ClientRequest) = {
        if (!cookies.isEmpty) {
          cr.getHeaders.put("Cookie", cookies)
        }
        val response = getNext.handle(cr)
        if (response.getCookies != null) {
          cookies.addAll(response.getCookies)
        }

        response
      }
    })

    val result: ClientResponse = client
      .resource("https://secure.bankofamerica.com/login/sign-in/entry/signOn.go")
      .`type`("application/x-www-form-urlencoded")
      .post(
        classOf[ClientResponse],
        "reason=&Access_ID=jtroesler&Access_ID_1=&Current_Passcode=&acct=&pswd=&from=&Customer_Type=&pmbutton=true&pmloginid=pmloginid&sitekeySignon=true&pm_fp=version%253D1%2526pm%255Ffpua%253Dmozilla%252F5%252E0%2520%2528x11%253B%2520linux%2520x86%255F64%2529%2520applewebkit%252F537%252E36%2520%2528khtml%252C%2520like%2520gecko%2529%2520chrome%252F37%252E0%252E2062%252E120%2520safari%252F537%252E36%257C5%252E0%2520%2528X11%253B%2520Linux%2520x86%255F64%2529%2520AppleWebKit%252F537%252E36%2520%2528KHTML%252C%2520like%2520Gecko%2529%2520Chrome%252F37%252E0%252E2062%252E120%2520Safari%252F537%252E36%257CLinux%2520x86%255F64%2526pm%255Ffpsc%253D24%257C1920%257C1080%257C1045%2526pm%255Ffpsw%253D%2526pm%255Ffptz%253D%252D6%2526pm%255Ffpln%253Dlang%253Den%252DUS%257Csyslang%253D%257Cuserlang%253D%2526pm%255Ffpjv%253D1%2526pm%255Ffpco%253D1&locale=en-us&dltoken=&state=&anotherOnlineIDFlag=N&hpPilot=true&defaultTextValue=Enter+your+Online+ID&id=*******")

    result

    val passwdResponse: ClientResponse = client
      .resource("https://secure.bankofamerica.com/login/sign-in/validatePassword.go")
      .`type`("application/x-www-form-urlencoded")
      .post(
        classOf[ClientResponse],
        "csrfTokenHidden=26ac4a2850028e71&f_variable=TF1%3B015%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3BMozilla%3BNetscape%3B5.0%2520%2528X11%253B%2520Linux%2520x86_64%2529%2520AppleWebKit%2F537.36%2520%2528KHTML%252C%2520like%2520Gecko%2529%2520Chrome%2F37.0.2062.120%2520Safari%2F537.36%3B20030107%3Bundefined%3Btrue%3B%3Btrue%3BLinux%2520x86_64%3Bundefined%3BMozilla%2F5.0%2520%2528X11%253B%2520Linux%2520x86_64%2529%2520AppleWebKit%2F537.36%2520%2528KHTML%252C%2520like%2520Gecko%2529%2520Chrome%2F37.0.2062.120%2520Safari%2F537.36%3Ben-US%3BISO-8859-1%3Bsecure.bankofamerica.com%3Bundefined%3Bundefined%3Bundefined%3Bundefined%3Btrue%3Btrue%3B1411961882102%3B-6%3B6%2F7%2F2005%25209%253A33%253A44%2520PM%3B1920%3B1080%3B%3B15.0%3B%3B%3B%3B%3B9%3B360%3B300%3B9%2F28%2F2014%252010%253A38%253A02%2520PM%3B24%3B1920%3B1045%3B0%3B0%3B%3B%3B%3B%3B%3BShockwave%2520Flash%257CShockwave%2520Flash%252015.0%2520r0%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B%3B15%3B&lpOlbResetErrorCounter=0&lpPasscodeErrorCounter=0&password=RD296nGkGq6vdVFYN6VF"
      )

    passwdResponse

    val dl = client.resource("https://secure.bankofamerica.com/myaccounts/details/deposit/download-transactions.go?adx=7421c213651001b1e9042af06eed35d7e617d675476465eebab80ac2d2a74485")
    .`type`("application/x-www-form-urlencoded")
    .post(classOf[ClientResponse],
      "selectedTransPeriod=&downloadTransactionType=customRange&searchBean.timeFrameStartDate=09%2F01%2F2014&searchBean.timeFrameEndDate=09%2F28%2F2014&formatType=qfx&searchBean.searchMoreOptionsPanelUsed=false")

    dl

    val entity = dl.getEntity(classOf[String])

    entity

    val root: TagNode = new HtmlCleaner().clean(entity)
    val form = root.findElementByAttValue("name","autosubmit",true,false)
    val formUrl = form.getAttributeByName("ACTION")

    val formData = new MultivaluedMapImpl()
    for {
      child <- form.getChildTagList
      if child.getName equalsIgnoreCase "INPUT"
      if child.hasAttribute("NAME")
    } {
      formData.add(child.getAttributeByName("NAME"), child.getAttributeByName("VALUE"))
    }

    val formresp = client
      .resource(formUrl)
      .`type`("application/x-www-form-urlencoded")
      .post(classOf[ClientResponse], formData)

    val dlpage: String = formresp.getEntity(classOf[String])

    dlpage
  }
}
