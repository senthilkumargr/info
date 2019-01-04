
import com.zoniac.dbaccess.DBAccess;
import com.zoniac.emailengine.YosemiteCompanyInfo;
import com.zoniac.logger.ZoniacLogger;
import com.zoniac.util.Debug;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Timer;

/**
 * Project : Zoniac Swift
 * File : BulkMailMessenger.java
 * Author : Saravanakumar Ramasamy <rsk@zoniac.com>
 * Version : 8.56.0
 *
 * Copyright (c) 2015, Zoniac Inc. All Rights Reserved.
 */
public class BulkMailMessenger extends LoadConfig {

  public static int maxRetryAttempt;
  public static int retryDelay;
  public static boolean remoteRunning = false;
  public static String webServerPrivateIP = null;
  public static Long jobDelay;
  public static Long efilServiceJobDelay;
  public static String passedCompanyIDs = null;
  public static YosemiteCompanyInfo commonInfo = new YosemiteCompanyInfo();
  public static LinkedHashMap corporateAddress = new LinkedHashMap();

  public static void main(String[] args) {

    try {

      //STEP 1 : load Framework.Properties
      loadFrameworkProperties();
      //STEP 2 : initializing Database Access , Query File and Messenger.Query
      initDatabaseAccess();
      //STEP 3 :    
      if (!checkDBAccess()) {
        return;
      }
      //STEP 4 : initializing PropertiesManager and Message Bundle
      initProperties();

      // GLOBAL Messenger Properties
      maxRetryAttempt = Integer.parseInt(getZoniacPropertiesManagerProperty("Email.MaximumRetryAttempt"));
      retryDelay = Integer.parseInt(getZoniacPropertiesManagerProperty("Email.RetryDelay"));
      jobDelay = Long.parseLong(getFrameworkProperties("JobListMessenger.JobDelay"));

      int enableEfilService = 0;
      if (getFrameworkProperties("JobListMessenger.efilService") != null) {
        enableEfilService = Integer.parseInt(getFrameworkProperties("JobListMessenger.efilService"));
      }
      efilServiceJobDelay = Long.parseLong(getFrameworkProperties("JobListMessenger.efilService.JobDelay"));

      if (getFrameworkProperties("JobListMessenger.IsRemoteRunning") != null && getFrameworkProperties("JobListMessenger.IsRemoteRunning").trim().length() > 0
              && (getFrameworkProperties("JobListMessenger.IsRemoteRunning").equalsIgnoreCase("yes")
              || getFrameworkProperties("JobListMessenger.IsRemoteRunning").equalsIgnoreCase("y")
              || getFrameworkProperties("JobListMessenger.IsRemoteRunning").equalsIgnoreCase("1"))) {
        remoteRunning = true;
      }

      if (getZoniacPropertiesManagerProperty("ZoniacWebServer.PrivateIP") != null && getZoniacPropertiesManagerProperty("ZoniacWebServer.PrivateIP").trim().length() > 0) {
        webServerPrivateIP = getZoniacPropertiesManagerProperty("ZoniacWebServer.PrivateIP");
      }

      //STEP 5 : read free email      
      readfreeemail();

      DBAccess dbAccess = DBAccess.getDefaultDBAccess();

      //STEP 6 : get SMPT Server detail
      getAvailableServerList(dbAccess);

      //STEP 7 : Create pool Object
      createCompanyWisePool();

      //STEP 8 : Ats Customer Domain List
      getAtsCustomerDomainList(dbAccess);

      //STEP 9: Update EfilSignup Link      
      ZoniacLogger.getLogger().info("EfilService : " + enableEfilService);
      if (enableEfilService == 1) {
        updateEfilSignupLink(dbAccess, commonInfo);
        EfilJobListServices efilServices = new EfilJobListServices();
        efilServices.start();
        ZoniacLogger.getLogger().info("Efil Service started ..");
      }      
            
      //STEP 10: Move Pending Records I TO Q
      movePendingRecords(dbAccess);

      if (dbAccess != null) {
        dbAccess.close();
      }
      dbAccess = null;

      Timer timer = new Timer();
      timer.scheduleAtFixedRate(new JobExecutor(), new Date(), 1 * 60 * 1000);//start every 1 min
      
      Timer timer2 = new Timer();
      timer2.scheduleAtFixedRate(new CorporateInfoUpdateRunner(corporateAddress, commonInfo), new Date(), 5 * 60 * 1000);//start every 5 min      
      
    } catch (Exception e) {
      Debug.print("ERROR: Main Program could not be located.");
      e.printStackTrace();
    }

  }

}
