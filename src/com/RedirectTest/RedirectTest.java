package com.RedirectTest;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.bean.ResponseBean;
import com.google.common.net.InternetDomainName;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
* Author : Vishal Pithe 
* SourcePep Technology.
* Hadapsar , Pune , 411028
* All rights reserved.
*/

public class RedirectTest 
{

	public static ResponseBean INSTANCE= new ResponseBean();
	private static final Pattern urlPattern = Pattern.compile("((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)", 
			Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
	private static final Pattern windoLocationPattern = Pattern.compile("window.location.+((https?):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)");

	static String baseUrl;

	/*static MongoClient 	mongoClientVPS		= 	new MongoClient("178.32.49.5",27017);
	static DB 			VPSDB 					=	mongoClientVPS.getDB("crawler"); 
	static DBCollection seedUrl 				= 	VPSDB.getCollection("sampleCrawldata");*/


	static MongoClient 	mongoClientLocal		= 	new MongoClient();
	static DB 			DB 						=	mongoClientLocal.getDB("crawler"); 
	static DBCollection seedUrl 				= 	DB.getCollection("SeedUrl");
	static DBCollection RedirectScenarious 		= 	DB.getCollection("RedirectSeedUrl_SeedUrl_New");

	public static void main(String[] args)
	{
		installCert();

		System.out.println(getUrlDomainName("pune.gov.in"));

		/*String link = "http://vcpanasonic.com/process/route/3018382076.html?refresh";
		if(link.contains("?"))
		{
			link = link.split("\\?")[0];
			System.out.println("? found");
		}
		System.out.println("Meta Refresh Url Found : "+link);
		 */

		System.exit(0);

		//System.out.println(getUrlDomainName("https//jobmote.com/"));


		DBCursor cursor = seedUrl.find(/*new BasicDBObject("status","false").append("authorityStatus", "redirect")*/);

		cursor.addOption(Bytes.QUERYOPTION_NOTIMEOUT);
		DBObject dbObject;
		BasicDBObject basicDBObject = new BasicDBObject();

		System.out.println("Data Count : "+cursor.size());

		int count=0;
		ArrayList<String> blackDomainList = new ArrayList<>();
		String prevLink="";
		while(cursor.hasNext())
		{

			try
			{
				dbObject = cursor.next();

				prevLink = baseUrl = dbObject.get("url").toString();

				if(getUrlDomainName(prevLink).equals(getUrlDomainName(dbObject.get("url").toString())))
				{
					count++;
					if(count>5)
					{
						blackDomainList.add(getUrlDomainName(prevLink));
						count = 0;
					}
				}

				//baseUrl = "http://seoul.craigslist.org/search/jjj?sort=date&is_telecommuting=1";

				if(!blackDomainList.contains(getUrlDomainName(baseUrl)))
				{

					System.out.println("\n********************************************************************************************************");
					
					INSTANCE = checkRedirectingURL(baseUrl,"Dynamic");	

					System.out.println("\n************************** RESULT ********************************");

					basicDBObject.put("url", dbObject.get("url").toString());

					if(INSTANCE.scenario.equals("Exception"))
					{
						System.out.println("Url      : "+baseUrl);
						basicDBObject.put("NewUrl", "");
					}
					else
					{
						System.out.println("Url      : " + INSTANCE.response.url());
						basicDBObject.put("NewUrl", INSTANCE.response.url().toString());
					}

					System.out.println("Status   : "+INSTANCE.status);
					System.out.println("Scenario : "+INSTANCE.scenario);



					basicDBObject.put("Status", INSTANCE.status);
					basicDBObject.put("Scenario", INSTANCE.scenario);

					RedirectScenarious.insert(basicDBObject);
					basicDBObject.clear();
				}
				else
				{
					System.out.println("Blocked..."+getUrlDomainName(baseUrl));
				}
			}
			catch(Exception e)
			{
				basicDBObject.clear();
				e.printStackTrace();
			}
		}
	}
/**
 * <h1> :	Redirection Common Code : </h1>
 * 
 * <b>Parameters : </b>
 * <ul>
 * <li> mainUrl : A url for redirect checking
 * <li> Type 	: A link type should be Static / Dynamic
 * </ul>
 * <p> Will hits the link with Redirection true.
 * And checks the main link with response final link. <P>
 * 
 * Method will covers following redirection scenarious<br><br>
 *    <b>Scenario</b>				 	==>  <b> Response status : scenario : response</b>
 * <ol><li> Main Domain Change			==> 	false  : Domain Change  : response
 * <li> Parked Page						==> 	false  : Parked Page  : response
 * <li> Jsoup Exception 				==>		false  : Exception      : null
 * <li> GET Parameters sequence change	==>		True if Parameters matches else Redirecting to=>link
 * <li> Auto Refresh tags	links		==>		True if links mainDomain matches else  Redirecting to=>link
 * </ol>
 * <b>Return :</b><br>
 * Status true if and only if above scenarious gets valid otherwise false.<br>
 * Response will be null in case of Jsoup Exception please handle this in code.
 * 
 */
	static ResponseBean checkRedirectingURL(String mainUrl,String LinkType) 
	{

		Response tempResponse;
		String responseUrl , tempMainurl , tempResponseUrl;

		//Removing # params from url
		//mainUrl = mainUrl.contains("#") ? mainUrl.split("#")[0] : mainUrl;

		try 
		{			
			INSTANCE.response = Jsoup.connect(mainUrl)
					.userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.2 (KHTML, like Gecko) Chrome/15.0.874.120 Safari/535.2")
					.referrer("http://www.google.com") 
					.header("Accept-Language", "en")
					.timeout(10000)
					.followRedirects(true)
					.execute();
			responseUrl = INSTANCE.response.url().toString();

			//System.out.println(INSTANCE.response.parse());
			System.out.println("Main URL : "+mainUrl);
			System.out.println(INSTANCE.response.statusCode()+"      : " + responseUrl);

			try
			{
				if(responseUrl.split("\\/")[2].contains(":"))
				{				
					responseUrl = responseUrl.replaceAll("(\\:[0-9]+)","");
					System.out.println("Port Number Removed : "+responseUrl);
				}

			}
			catch(Exception e)
			{

			}
			/**
			 * Checking for Meta Refresh Tag
			 * For those links whose response code is 200 but auto redirects after some time
			 */
			tempResponse = checkForMetaRefresh(INSTANCE.response);
			if(tempResponse==null)
			{
				return INSTANCE;
			}
			if(INSTANCE.response != tempResponse)
			{
				if(LinkType.equals("Static"))
				{
					INSTANCE.response = tempResponse;
					mainUrl = responseUrl = tempResponse.url().toString();

				}
				else
				{
					responseUrl = tempResponse.url().toString();
				}
				/*
				 * ReCalling checkRedirectingURL() if metaRefresh link is redirecting
				 */
				if(tempResponse.statusCode() > 300 && tempResponse.statusCode() < 310)
					checkRedirectingURL(responseUrl,LinkType); 
			}

			/**
			 * Checking for Body Onload tag link
			 * For those links whose response code is 200 but gets redirects after page load
			 */
			tempResponse = checkForBodyOnload(INSTANCE.response);

			if(tempResponse==null)
			{
				return INSTANCE;
			}
			if(INSTANCE.response != tempResponse)
			{
				if(LinkType.equals("Static"))
				{
					INSTANCE.response = tempResponse;
					mainUrl = responseUrl = tempResponse.url().toString();	
				}
				else
				{
					responseUrl = tempResponse.url().toString();
				}
				/*
				 * ReCalling checkRedirectingURL() if metaRefresh link is redirecting
				 */
				if(tempResponse.statusCode() > 300 && tempResponse.statusCode() < 310)
					checkRedirectingURL(responseUrl,LinkType); 
			}			

			/**
			 * Checking for Window.Location
			 * For those links whose response code is 200 but gets redirects after page load
			 */
			/*	
			 tempResponse = checkForWindowLocation(INSTANCE.response);

			if(tempResponse==null)
			{
				return INSTANCE;
			}
			if(INSTANCE.response != tempResponse )
			{
				if(Type.equals("Static"))
				{
					INSTANCE.response = tempResponse;
					mainUrl = responseUrl = tempResponse.url().toString();	
				}
				else
				{
					responseUrl = tempResponse.url().toString();
				}

			 * ReCalling checkRedirectingURL() if metaRefresh link is redirecting

				if(tempResponse.statusCode() > 300 && tempResponse.statusCode() < 310)
					checkRedirectingURL(responseUrl,Type); 
			}	*/	
			
			if(responseUrl.matches("https?://w+[0-9]+\\..*"))
			{
				INSTANCE.status = false;
				INSTANCE.scenario = "Parked Page";
				return INSTANCE;
			}
			

			/**
			 * Checking MainDomain Change
			 */
			if(!getUrlDomainName(responseUrl).equals(getUrlDomainName(mainUrl)))
			{				
				INSTANCE.status = false;
				INSTANCE.scenario = "Domain Change";
				return INSTANCE;
			}
			else if(LinkType.equals("Static"))
			{
				INSTANCE.status = true;
				INSTANCE.scenario = "Valid";
				return INSTANCE;
			}
			/*
			 * Removing last / from response + main url
			 */
			tempResponseUrl = responseUrl.endsWith("/") ? responseUrl.substring(0, responseUrl.length()-1) : responseUrl ;
			tempMainurl = mainUrl.endsWith("/") ? mainUrl.substring(0, mainUrl.length()-1) : mainUrl ;
			/*
			 * Removing http(s)://www. from url
			 * For some links www gets appended or gets removed after redirect
			 */
			tempResponseUrl = tempResponseUrl.replaceAll("(https?:\\/\\/)?(w+.?\\.)?", "").toLowerCase();
			tempMainurl = tempMainurl.replaceAll("(https?:\\/\\/)?(w+.?\\.)?", "").toLowerCase();

			if(!tempMainurl.equals(tempResponseUrl))
			{
				if((mainUrl.split("\\?").length)>1)
				{
					/**
					 * Checking for parameters valid Sequence Change
					 */
					if(!checkForParameterChange(mainUrl,responseUrl))
					{
						INSTANCE.status = false;
						INSTANCE.scenario = "Redirecting to => "+responseUrl;
						return INSTANCE;
					}
				}
				else
				{
					INSTANCE.status = false;
					INSTANCE.scenario = "Redirecting to => "+responseUrl;
					return INSTANCE;
				}
			}

		} 		
		catch (Exception e) 
		{
			System.out.println(e.getClass().getName());
			INSTANCE.status = false;
			INSTANCE.scenario = "Exception";
			return INSTANCE;

		}

		INSTANCE.status = true;
		INSTANCE.scenario = "Valid";
		return INSTANCE;

	}


	static Response checkForWindowLocation(Response response)
	{

		String link;
		try 
		{

			Matcher matcher = windoLocationPattern.matcher(response.parse().toString());

			if (matcher.find()) 
			{
				link = response.parse().toString().substring(matcher.start(), matcher.end());
				matcher = urlPattern.matcher(link);

				if (matcher.find()) 
				{
					link = link.substring(matcher.start(), matcher.end());
					System.out.println("Window Location Url Found :  : "+link);
					if(getUrlDomainName(link).equals(getUrlDomainName(response.url().toString())))
					{
						response = Jsoup.connect(link)
								.userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.2 (KHTML, like Gecko) Chrome/15.0.874.120 Safari/535.2")
								.referrer("http://www.google.com") 
								.header("Accept-Language", "en")
								.timeout(10000)
								.followRedirects(false)
								.execute();
					}
					else
					{
						INSTANCE.status = false;
						INSTANCE.scenario = "Redirecting to => "+link;
						return null;
					}
				}
			}
		}
		catch (IOException e) 
		{
			INSTANCE.status = false;
			INSTANCE.scenario = "Exception";
			return null;
		}
		return response;
	}


	/**
	 * ---- Description ---
	 * Checks for body onload tag in doc
	 * If present then extract the url from tag and hits it
	 * If domian matches returs response else null
	 * @param Respone
	 * @return Respone
	 */
	public static Response checkForBodyOnload(Response response)
	{
		String link;
		try 
		{

			for (Element refresh : response.parse().select("body[onload]")) 
			{
				Matcher matcher = urlPattern.matcher(refresh.attr("onload"));
				if (matcher.find()) 
				{
					link = refresh.attr("onload").substring(matcher.start(), matcher.end());
					System.out.println("Body OnLoad Link found : "+ link );

					link = validateLink(link);
					if(getUrlDomainName(link).equals(getUrlDomainName(response.url().toString())))
					{
						response = Jsoup.connect(link)
								.userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.2 (KHTML, like Gecko) Chrome/15.0.874.120 Safari/535.2")
								.referrer("http://www.google.com") 
								.header("Accept-Language", "en")
								.timeout(10000)
								.followRedirects(false)
								.execute();
						if(!getUrlDomainName(link).equals(getUrlDomainName(response.url().toString())))
						{
							INSTANCE.status = false;
							INSTANCE.scenario = "Redirecting to => "+response.url().toString();
							return null;
						}
					}
					else
					{
						INSTANCE.status = false;
						INSTANCE.scenario = "Redirecting to => "+link;
						return null;
					}
				}
			}

		}
		catch (IOException e) 
		{

			INSTANCE.status = false;
			INSTANCE.scenario = "Exception";
			return null;

		}
		return response;

	}

	/**
	 * Checks for meta refresh tag in doc if present then extract the url from tag and hits it
	 * @param response
	 * @return
	 */

	static Response checkForMetaRefresh(Response response)	
	{
		String link;
		try 
		{
			int count = 0;
			for (Element refresh : response.parse().select("html head meta[http-equiv=refresh]")) 
			{
				if(count==1)
					break;

				link = refresh.attr("content").split("=")[1];

				link = validateLink(link);

				System.out.println("Meta Refresh Url Found : "+link);

				if(getUrlDomainName(link).equals(getUrlDomainName(response.url().toString())))
				{
					response = Jsoup.connect(link)
							.userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.2 (KHTML, like Gecko) Chrome/15.0.874.120 Safari/535.2")
							.referrer("http://www.google.com") 
							.header("Accept-Language", "en")
							.timeout(10000)
							.followRedirects(true)
							.execute();
					if(!getUrlDomainName(link).equals(getUrlDomainName(response.url().toString())))
					{
						INSTANCE.status = false;
						INSTANCE.scenario = "Redirecting to => "+response.url().toString();
						return null;
					}

				}
				else
				{
					INSTANCE.status = false;
					INSTANCE.scenario = "Redirecting to => "+link;
					return null;
				}
				count++;
			}

		}
		catch (IOException e) 
		{
			INSTANCE.status = false;
			INSTANCE.scenario = "Exception";
			return null;
		}
		return response;
	}

	static String validateLink(String link)
	{

		if(link.startsWith("http"))
			return link;

		if(link.trim().startsWith("/"))  //If redirected link starts with slash.
		{
			if(baseUrl.endsWith("/")) //If redirected link have only slash.
			{

				return(baseUrl.substring(0, baseUrl.length()-1)+link);
			}
			else
			{
				return(baseUrl+link);
			}
		}
		else
		{	
			return(baseUrl+"/"+link);
		}				
	}

	/**
	 * Checking for url parameters presence in url1 and url2 by taking part of link aftre ?
	 * and splitting by = then by splitting any Special Characters .
	 * If parameters are match in upto total param list - 2 then true else false
	 * @param url1
	 * @param url2
	 * @return Boolean
	 */
	static Boolean checkForParameterChange(String url1 , String url2)
	{
		try
		{
			int matchCount=0;
			ArrayList<String> url1Params = new ArrayList<>();
			//System.out.println(url1);
			//System.out.println(url2);
			String urlArr[] = url1.split("\\?")[1].split("\\=");

			for(int i = 0 ; i< urlArr.length ; i++)
			{
				for(String tempStr2 :urlArr[i].split("[\\W]"))
				{
					if(!tempStr2.isEmpty())
						url1Params.add(tempStr2);
				}
			}

			System.out.println(url1Params);

			urlArr = url2.split("\\?")[1].split("\\=");

			System.out.println(Arrays.toString(urlArr));
			for(int i = 0 ; i< urlArr.length ; i++)
			{
				for(String tempStr2 :urlArr[i].split("[\\W]"))
				{
					if(url1Params.contains(tempStr2))
						matchCount++;
				}
			}

			System.out.println(matchCount);

			if(url1Params.size() > 2)
			{
				if(matchCount >= url1Params.size()-2 )
					return true;
			}
			else
			{
				if(matchCount == url1Params.size())
					return true;
			}
		}
		catch(Exception e){}
		return false;
	}


	/**
	 * Will extract the mainDomain from url and return
	 * i/p: http://london.creiglist.co.uk
	 * o/p: creiglist
	 * @param url
	 * @return mainDomain
	 */
	static String getUrlDomainName(String url)
	{

		try
		{
			InternetDomainName fullDomainName = InternetDomainName.from(new URL(url).getHost()).topPrivateDomain();
			return fullDomainName.parts().iterator().next().replaceAll("[^a-zA-Z0-9]", "");
		}
		catch(Exception e)
		{		
			String domainName = new String(url);
			
			int index = domainName.indexOf("://");
			
			if (index != -1) 
				domainName = domainName.substring(index + 3);
			index = domainName.indexOf('/');
			
			if (index != -1) 
				domainName = domainName.substring(0, index);
			
			InternetDomainName fullDomainName = InternetDomainName.from(domainName).topPrivateDomain();
			return fullDomainName.parts().iterator().next().replaceAll("[^a-zA-Z0-9]", "");
			
		}
	}
	/**
	 * Method installs th certificates for SSL Handshake
	 */
	static void installCert()
	{
		// Create a trust manager that does not validate certificate chains like the
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				// No need to implement.
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
				// No need to implement.
			}
		} };

		// To handle ssl handshake exception

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			System.out.println(e);

		}
	}
}
