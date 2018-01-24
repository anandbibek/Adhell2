package com.fiendfyre.AdHell2.blocker;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import com.fiendfyre.AdHell2.App;
import com.fiendfyre.AdHell2.MainActivity;
import com.fiendfyre.AdHell2.db.AppDatabase;
import com.fiendfyre.AdHell2.db.entity.AppInfo;
import com.fiendfyre.AdHell2.db.entity.BlockUrl;
import com.fiendfyre.AdHell2.db.entity.BlockUrlProvider;
import com.fiendfyre.AdHell2.db.entity.UserBlockUrl;
import com.fiendfyre.AdHell2.db.entity.WhiteUrl;
import com.fiendfyre.AdHell2.utils.BlockUrlPatternsMatch;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;
import com.sec.enterprise.firewall.FirewallRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

public class ContentBlocker56 implements ContentBlocker {
    private static ContentBlocker56 mInstance = null;
    private final String TAG = ContentBlocker56.class.getCanonicalName();

    @Nullable
    @Inject
    Firewall mFirewall;
    @Inject
    AppDatabase appDatabase;
    private int urlBlockLimit = 2700, denyListLimit = 2500;

    private ContentBlocker56() {
        App.get().getAppComponent().inject(this);
    }

    public static ContentBlocker56 getInstance() {
        if (mInstance == null) {
            mInstance = getSync();
        }
        return mInstance;
    }

    private static synchronized ContentBlocker56 getSync() {
        if (mInstance == null) {
            mInstance = new ContentBlocker56();
        }
        return mInstance;
    }

    @Override
    public boolean enableBlocker() {
        if (isEnabled()) {
            disableBlocker();
        }

        /*
        BlockUrlProvider standardBlockUrlProvider =
                appDatabase.blockUrlProviderDao().getByUrl(MainActivity.ADHELL_STANDARD_PACKAGE);
        List<BlockUrl> standardList = appDatabase.blockUrlDao().getUrlsByProviderId(standardBlockUrlProvider.id);
        */

        Set<BlockUrl> finalBlockList = new HashSet<>();
        //finalBlockList.addAll(standardList);
        List<BlockUrlProvider> blockUrlProviders = appDatabase.blockUrlProviderDao().getBlockUrlProviderBySelectedFlag(1);

        for (BlockUrlProvider blockUrlProvider : blockUrlProviders) {
            /*
            if (blockUrlProvider.url.equals(MainActivity.ADHELL_STANDARD_PACKAGE)) {
                continue;
            }
            */
            Log.i(TAG, "Included url provider: " + blockUrlProvider.url);
            List<BlockUrl> blockUrls = appDatabase.blockUrlDao().getUrlsByProviderId(blockUrlProvider.id);
            if (finalBlockList.size() + blockUrls.size() <= this.urlBlockLimit - 100) {
                finalBlockList.addAll(blockUrls);
            } else {
                int remain = this.urlBlockLimit - finalBlockList.size();
                if (remain < blockUrls.size()) {
                    blockUrls = blockUrls.subList(0, remain);
                }
                finalBlockList.addAll(blockUrls);
                break;
            }
        }
        List<WhiteUrl> whiteUrls = appDatabase.whiteUrlDao().getAll2();

        List<String> whiteUrlsString = new ArrayList<>();
        for (WhiteUrl whiteUrl : whiteUrls) {
            whiteUrlsString.add(whiteUrl.url);
        }

        List<String> denyList = new ArrayList<>();
        for (BlockUrl blockUrl : finalBlockList) {
            /*
            if (Patterns.WEB_URL.matcher(blockUrl.url).matches()) {
                if (whiteUrlsString.contains(blockUrl.url)) {
                    continue;
                }
                denyList.add("*" + blockUrl.url + "*");
            }
            */

            if (whiteUrlsString.contains(blockUrl.url)) {
                continue;
            }

            // If a wildcard entry is passed, bypass current URL filter
            if(blockUrl.url.contains("*"))
            {
                Log.d(TAG, "Wildcard detected --> " + blockUrl.url + " requires validation.");

                // Check the wildcard is valid
                boolean validWildcard = BlockUrlPatternsMatch.wildcardValid(blockUrl.url);

                // If it isn't valid, skip it
                if(!validWildcard)
                {
                    Log.d(TAG, blockUrl.url + " is not a valid wildcard.");
                    continue;
                }

                Log.d(TAG, "Wildcard verified.");

                final String urlReady = blockUrl.url;

                denyList.add(urlReady);
            }
            // If it a wildcard isn't detected, let's use AdHell's original processing.
            else if (Patterns.WEB_URL.matcher(blockUrl.url).matches())
            {
                final String urlReady = "*" + blockUrl.url;

                Log.d(TAG, "Adding rule: " + urlReady);

                denyList.add(urlReady);
            }

        }

        List<UserBlockUrl> userBlockUrls = appDatabase.userBlockUrlDao().getAll2();

        if (userBlockUrls != null && userBlockUrls.size() > 0) {
            Log.i(TAG, "UserBlockUrls size: " + userBlockUrls.size());
            for (UserBlockUrl userBlockUrl : userBlockUrls) {
                /*
                if (Patterns.WEB_URL.matcher(userBlockUrl.url).matches()) {
                    denyList.add("*" + userBlockUrl.url + "*");
                    Log.i(TAG, "UserBlockUrl: " + userBlockUrl.url);
                }
                */

                if (Patterns.WEB_URL.matcher(userBlockUrl.url).matches()) {

                    final String urlReady = "*" + userBlockUrl.url + "*";

                    denyList.add(urlReady);

                    Log.i(TAG, "UserBlockUrl: " + urlReady);
                }

            }
        } else {
            Log.i(TAG, "UserBlockUrls is empty.");
        }

        Log.d(TAG, "Number of block list: " + denyList.size());
        List<String> allowList = new ArrayList<>();
        List<DomainFilterRule> rules = new ArrayList<>();
        AppIdentity appIdentity = new AppIdentity("*", null);

        //Trying to add a single rule containing more than 15000 urls returns NULL response
        //So, let's break down denyList into chunks and make multiple rules
        int start = 0, end = denyListLimit;
        do {
            rules.add(new DomainFilterRule(appIdentity, denyList.subList(start,end), allowList));
            start = end;
            end = (end+denyListLimit)>denyList.size()? denyList.size(): end+denyListLimit;
        } while (start!=end);

        //rules.add(new DomainFilterRule(appIdentity, denyList, allowList));
        List<String> superAllow = new ArrayList<>();
        superAllow.add("*");
        List<AppInfo> appInfos = appDatabase.applicationInfoDao().getWhitelistedApps();
        Log.d(TAG, "Whitelisted apps size: " + appInfos.size());
        for (AppInfo app : appInfos) {
            Log.d(TAG, app.packageName);
            rules.add(new DomainFilterRule(new AppIdentity(app.packageName, null), new ArrayList<>(), superAllow));
        }

        try {
            /* Try to block Port 53
               This is now necessary for Chrome
             */

            Log.d(TAG, "Adding: DENY PORT 53");

            // Number of rules
            int numRules = 2;
            // Declare new firewall rule variable
            FirewallRule[] portRules = new FirewallRule[numRules];

            // Add deny rules for DNS port (53)
            portRules[0] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV4);
            portRules[0].setIpAddress("*");
            portRules[0].setPortNumber("53");

            portRules[1] = new FirewallRule(FirewallRule.RuleType.DENY, Firewall.AddressType.IPV6);
            portRules[1].setIpAddress("*");
            portRules[1].setPortNumber("53");

            // Send rules to the firewall
            FirewallResponse[] response = mFirewall.addRules(portRules);

            // Define bitmask
            int bitmask = (Firewall.FIREWALL_ALLOW_RULE | Firewall.FIREWALL_DENY_RULE);

            // Query firewall for IP rules
            FirewallRule[] allowAndDenyRules = mFirewall.getRules(bitmask, null);

            // Output to log
            Log.d(TAG,"Firewall IP Rules:" + Arrays.toString(allowAndDenyRules));
        }
        catch (SecurityException ex)
        {
            Log.e(TAG, "Failed to add PORT rule.", ex);
            return false;
        }

        try {
            Log.d(TAG, "Adding: DENY DOMAINS");
            //each iteration (2500 urls) takes up to 30secs on S8+
            for(int i = 0 ; i<rules.size(); i++) {
                FirewallResponse[] response = mFirewall.addDomainFilterRules(rules.subList(i,i+1));
                Log.d(TAG, "Added rule "+ i + " . Response : " + (response!=null? response[0].getMessage() : "NULL"));
            }

            if (!mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(true);
            }
            if (!mFirewall.isDomainFilterReportEnabled()) {
                Log.d(TAG, "Enabling filewall report");
                mFirewall.enableDomainFilterReport(true);
            }
            return true;
//            if(response==null){
//                Log.i(TAG, "Adhell enabling failed. NULL response. List size too big? ");
//                throw new Exception();
//            }
//            if (FirewallResponse.Result.SUCCESS == response[0].getResult()) {
//                Log.i(TAG, "Adhell enabled " + response[0].getMessage());
//                return true;
//            } else {
//                Log.i(TAG, "Adhell enabling failed " + response[0].getMessage());
//                return false;
//            }
        } catch (Exception ex) {
            Log.e(TAG, "Adhell enabling failed", ex);
            return false;
        }
    }

    @Override
    public boolean disableBlocker() {
        FirewallResponse[] response;
        try {
            // Clear IP rules
            response = mFirewall.clearRules(Firewall.FIREWALL_ALL_RULES);

            // Clear domain filter rules
            response = mFirewall.removeDomainFilterRules(DomainFilterRule.CLEAR_ALL);

            Log.i(TAG, "disableBlocker " + response[0].getMessage());
            if (mFirewall.isFirewallEnabled()) {
                mFirewall.enableFirewall(false);
            }
            if (mFirewall.isDomainFilterReportEnabled()) {
                mFirewall.enableDomainFilterReport(false);
            }
        } catch (SecurityException ex) {
            Log.e(TAG, "Failed to remove firewall rules", ex);
            return false;
        }
        return true;
    }

    @Override
    public boolean isEnabled() {
        return mFirewall.isFirewallEnabled();
    }

    public void setUrlBlockLimit(int urlBlockLimit) {
        this.urlBlockLimit = urlBlockLimit;
    }

}