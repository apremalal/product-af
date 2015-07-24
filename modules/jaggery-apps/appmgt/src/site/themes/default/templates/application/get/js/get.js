// global data store
var currentVersion = "trunk";
var isInit = true;
var devStudioLink = "http://wso2.com/more-downloads/developer-studio/";

// page initialization
$(document).ready(function() {
    // initialize page and handlers
    initPageView();
    // load initial data to the page
    loadTeamInfo();
    loadAppInfoFromServer(currentVersion);
});

// wrapping functions
function initPageView() {
    loadAppIcon(applicationInfo.key);
    addSidePaneClickHandlers();
}

// Icon initialization
function loadAppIcon(appKey) {
    jagg.post("../blocks/application/get/ajax/list.jag", {
        action: "isAppIconAvailable",
        applicationKey: appKey
    },

    function (result) {
        if(result == 101) {
            // Application icon is not available, set the default
            $(".app-icon").attr('src', servicePath  + '/site/themes/default/assets/img/app_icon.png');
            console.info("101");
        } else {
            $(".app-icon").attr('src', iconUrl);
        }
    }, function (jqXHR, textStatus, errorThrown) {
        console.log("Could not load the application icon!");
    });
}

// adding notification panel
function addSidePaneClickHandlers() {
    $('.side-pane-trigger').click(function() {
        var rightPane = $('.right-pane');
        var leftPane = $('.left-pane');
        if (rightPane.hasClass('visible')) {
            rightPane.animate({"left":"0em"}, "slow").removeClass('visible');
            leftPane.animate({"left":"-18em"}, "slow");
            $(this).find('i').removeClass('fa-arrow-left').addClass('fa-reorder');
        } else {
            rightPane.animate({"left":"18em"}, "slow").addClass('visible');
            leftPane.animate({"left":"0em"}, "slow");
            $(this).find('i').removeClass('fa-reorder').addClass('fa-arrow-left');
        }
    });

    $('.notification-pane-trigger').click(function() {
        var notificationPane = $('.notification-pane');
        if(notificationPane.hasClass('visible')) {
            notificationPane.animate({"right":"0em"}, "slow").removeClass('visible');
        } else {
            notificationPane.animate({"right":"-24em"}, "slow").addClass('visible');
        }
    });
}

// load team information
function loadTeamInfo() {
    jagg.post("../blocks/application/user/get/ajax/list.jag", {
            action:"getUsersOfApplication",
            applicationKey:applicationInfo.key
    },function (result) {
        var teamMemberCount = 0;
        if(result) {
            var applicationUserList = JSON.parse(result);
            teamMemberCount = applicationUserList.length;
            $("#teamCount").html(teamMemberCount);
        }
    },function (jqXHR, textStatus, errorThrown) {
        jagg.message({content:'Could not load team information!', type:'error', id:'notification' });
    });
}

// load main application data from server side
function loadAppInfoFromServer(version) {
    // show loading image
    $('.loader').loading('show');
    $('.loading-overlay').overlay('show');

    jagg.post("../blocks/application/get/ajax/list.jag", {
          action:"getAppVersionsInStages",
          applicationKey: applicationInfo.key,
          userName: $("#userName").attr('value')
    },function (result) {
            var resultData = jQuery.parseJSON(result);
            if (resultData.length > 0) {
                // get the relevant application info object
                // since this always gives only one element array, take the first element
                var appInfo = resultData[0];
                var currentAppInfo = filterAppVersionInfo(appInfo, version);

                // load application version specific data
                // note : need to hide overlay in the final ajax call's callback function
                loadDatabaseInfo(currentAppInfo);
                loadLaunchInfo(appInfo, currentAppInfo);
                loadLifeCycleManagementInfo(currentAppInfo);
                loadRepoAndBuildsInfo(currentAppInfo);
                loadIssuesInfo(version);
            }
      },function (jqXHR, textStatus, errorThrown) {
            if (jqXHR.status != 0) {
                jagg.message({content:'Could not load Application information', type:'error', id:'notification' });
            }
      });
}

// filter application data by version
function filterAppVersionInfo(appInfo, version) {
    var appVersionInfo = null;
    if (!appInfo || !version) {
        return appVersionInfo;
    }
    for (var i in appInfo.versions) {
        var versionInfo = appInfo.versions[i];
        if (versionInfo.version == version) {
            appVersionInfo = versionInfo;
            break;
        }
    }
    return appVersionInfo;
}

// load database information
function loadDatabaseInfo(appVersionInfo) {
    jagg.post("../blocks/resources/database/add/ajax/add.jag", {
            action : "getDatabasesForStage",
            applicationKey : applicationInfo.key,
            stage : appVersionInfo.stage
    },function (result) {
        var databaseList = JSON.parse(result);
        databaseCount = databaseList.length;
        $("#databaseCount").html(databaseCount);
    },function (jqXHR, textStatus, errorThrown) {
        jagg.message({content:'Could not load database information!', type:'error', id:'notification' });
    });
}


// load launch url information
function loadLaunchInfo(appInfo, currentAppInfo) {
    var versionOptionListHtml = "";

    for (var i in appInfo.versions) {
        var versionInfo = appInfo.versions[i];
        versionOptionListHtml += "<option>";
        versionOptionListHtml += versionInfo.version;
        versionOptionListHtml += "</option>";
    }
    $("#appVersionList").html(versionOptionListHtml);
    $('#appVersionList').val(currentAppInfo.version);

    // set launch app url
    loadLaunchUrl(currentAppInfo.version, currentAppInfo.appStage);

    $('#btn-launchApp').click(function() {
        var appUrl = $('#btn-launchApp').attr("url");
        var newWindow = window.open('','_blank');
        newWindow.location = appUrl;
    });

    // add listener for cloud envy
    $('#createCodeEnvyUrl').click(function() {
        if(!isCodeEditorSupported) {
            jagg.message({content: "Code editor not supported for the " + applicationInfo.type + " application type!", type: 'error', id:'message_id'});
        } else {
            createCodeEnvyUrl(currentAppInfo.repoURL);
        }
    });

    // add listener for developer studio
    $('#localIde').click(function() {
        var newWindow = window.open('','_blank');
        newWindow.location = devStudioLink;
    });

    $("#appVersionList").change(function() {
        // reload page info for the selected version
        currentVersion = this.value;
        loadAppInfoFromServer(currentVersion);
    });

}

//// load application launch url
function loadLaunchUrl(version, stage) {
    jagg.post("../blocks/application/get/ajax/list.jag", {
       action: "getMetaDataForAppVersion",
       applicationKey: applicationInfo.key,
       version: version,
       stage: stage,
       state: "started",
       type: applicationInfo.type
    }, function (result) {
        if(result) {
           var resJSON = jQuery.parseJSON(result);
           var appURL = "http://appserver.dev.appfactory.private.wso2.com";
           if(resJSON.url) {
               appURL = resJSON.url;
           }

           // display app url
           var repoUrlHtml = "URL : " + appURL;
           $("#app-version-url").html(repoUrlHtml);
           // set url to launch button
           $('#btn-launchApp').attr({url:appURL});
        }
       }
    );
}


// load code envy editor
function createCodeEnvyUrl(gitURL) {
    jagg.post("../blocks/reposBuilds/list/ajax/list.jag", {
            action: "createCodeEnvyUrl",
            gitURL: gitURL,
            applicationKey: applicationInfo.key,
            appType: applicationInfo.type,
            version:currentVersion
        }, function (result) {
            if(result) {
                var newWindow = window.open('','_blank');
                newWindow.location = result;
            } else {
                jagg.message({content: "Failed creating the Codenvy workspace URL! ", type: 'error', id:'message_id'});
            }
        });
}

// load life cycle management information
function loadLifeCycleManagementInfo(appVersionInfo) {
    if(appVersionInfo) {
        var stage = appVersionInfo.appStage;
        $("#appStage").html(stage);
    }
}

// load repository and build information
function loadRepoAndBuildsInfo(appVersionInfo) {
    if (appVersionInfo) {
        var versionStage = appVersionInfo.stage;
        var lastBuildInfo = appVersionInfo.lastBuildResult;
        var buildSplitted = lastBuildInfo.split(' ');

        var buildNumber = 0;
        var buildStatus = "";

        if (buildSplitted.length > 1 && buildSplitted[1] != "null") {
            buildNumber = buildSplitted[1];
        }

        if (buildSplitted.length > 2 && buildSplitted[2] != "null") {
            buildStatus = buildSplitted[2];
        }

        // show data in the UI
        $("#lifecycle-mgt-main").html("Application <strong>" + applicationInfo.name + "</strong> is in <br> <strong>" +
            versionStage + "</strong> Stage");
        $("#success-and-fail-ids").html("Build " + buildNumber + " " + buildStatus);
    }
}

// load issue tracking information
function loadIssuesInfo() {
    jagg.post("../blocks/issuetracker/list/ajax/list.jag", {
        action:"getIssuesSummary",
        applicationKey:applicationInfo.key
    },function (result) {
        var resultJson = JSON.parse(result);
        var issueData = {'Improvement':'0','NEW_FEATURE':'0','BUG':'0', 'Task': '0'};
        for(var key in resultJson) {
            if (resultJson.hasOwnProperty(key) && key === currentVersion) {
                issueData = resultJson[currentVersion];
                break;
            }
        }

        // render issue data
        var issueSegment = formatCount(issueData.BUG);
        issueSegment += " Bugs<br>";
        issueSegment +=  formatCount(issueData.NEW_FEATURE);
        issueSegment += " Features<br>";
        issueSegment += formatCount(issueData.Improvement);
        issueSegment += " Improvements<br>";
        issueSegment += formatCount(issueData.Task);
        issueSegment += " Tasks";
        $("#issueCount").html(issueSegment);


        // hide loading image after loading all the version specific data
        $('.loader').loading('hide');
        $('.loading-overlay').overlay('hide');

    },function (jqXHR, textStatus, errorThrown) {
        jagg.message({content:'Could not load Application issue information!', type:'error', id:'notification' });
    });
}

// Utility Functions Goes Here
// number formatting util function
function formatCount(count) {
   if(count) {
       return count;
   }
   return 0;
}