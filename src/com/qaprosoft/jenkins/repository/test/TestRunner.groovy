package com.qaprosoft.jenkins.repository.test

import com.qaprosoft.jenkins.repository.pipeline.v2.Runner

class TestRunner extends Runner {
	
	@Override
	protected void customPrepareForAndroid(params) {
		context.echo "TestRunner->prepareForAndroid"
		
		def zafira_project = params.get("zafira_project")
		def _env = params.get("env")

		if ("SING".equals(zafira_project)) {
			context.echo "ENV: ${_env}"
			switch(_env) {
				case "BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingAndroidQaReleaseBeta/sing_android-playstore-release-beta-.*${build}.*.apk")
					break
				case "MASTER_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingAndroidQaMasterBeta/sing_android-playstore-master-beta-.*${build}.*.apk")
					break
				case "MASTER_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt81/sing_android-playstore-master-int-.*.apk")
					break
				case "MASTER_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt83/sing_android-playstore-master-prod-.*.apk")
					break
				case "MASTER_STG":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt82/sing_android-playstore-master-stg-.*.apk")
					break
				case "INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt84/sing_android-playstore-release-int-.*.apk")
					break
				case "PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt85/sing_android-playstore-release-prod-.*.apk")
					break
				case "STG":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/bt86/sing_android-playstore-release-stg-.*.apk")
					break
				case "QA_SUPERPOWERED_PROD_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SuperpoweredProdBuilds_SingAndroidQaSuperpoweredProdInt/sing_android-playstore-superpowered_prod-int-.*.apk")
					break
				case "QA_SUPERPOWERED_REC_TYPE_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SuperpoweredRecTypeBuilds_SingAndroidQaSuperpoweredRecTypeBeta/sing_android-playstore-superpowered_rec_type-beta-.*.apk")
					break
				case "QA_DEV_DSHARE_CONTROL_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_DevDshareControlBuilds_SingAndroidQaDevDshareControlInt/sing_android-playstore-dev_dshare_control-int-.*.apk")
					break
				case "QA_PERFORMANCE_UPLOAD_MANAGER_V2_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_PerformanceUploadManagerV2builds_SingAndroidQaPerformanceUploadManag/sing_android-playstore-performance_upload_manager_v2-prod-.*.apk")
					break
				case "QA_FIND_FRIENDS_PHASE1_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FindFriendsPhase1builds_SingAndroidQaFindFriendsPhase1beta/sing_android-playstore-find_friends_phase_1-beta-.*.apk")
					break
				case "QA_FIND_FRIENDS_PHASE1_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FindFriendsPhase1builds_SingAndroidQaFindFriendsPhase1prod/sing_android-playstore-find_friends_phase_1-prod-.*.apk")
					break
				case "QA_ARMSTRONG_AUTO_LOGIN_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_ArmstrongAutoLoginBuilds_SingAndroidQaArmstrongAutoLoginProd/sing_android-playstore-armstrong_auto_login-prod-.*.apk")
					break
				case "QA_CONTINUOUS_PLAY_PHASE3_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_ContinuousPlayPhase3builds_SingAndroidQaContinuousPlayPhase3beta/sing_android-playstore-continuous_play_phase_3-beta-.*.apk")
					break
				case "QA_SINGLE_EGL_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SingleEGLBuilds_SingAndroidQaSingleEGLInt/sing_android-playstore-singleEGL-int-.*.apk")
					break
				case "QA_FRAUDJUST_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FraudjustBuilds_SingAndroidQaFraudjustInt/sing_android-playstore-fraudjust-int-.*.apk")
					break
				case "QA_JOINERS_CHOICE_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_JoinersChoiceBuilds_SingAndroidQaJoinersChoiceInt/sing_android-playstore-joiners_choice-int-.*.apk")
					break
				case "QA_APPEVENTS_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_AppeventsBuilds_SingAndroidQaAppeventsProd/sing_android-playstore-appevents-prod-.*.apk")
					break
				case "QA_RENDERED_FILED_DELETION_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15040builds_SingAndroidQaSa15040int/sing_android-playstore-sa_15040-int-.*.apk")
					break
				case "QA_RENDERED_FILED_DELETION_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15040builds_SingAndroidQaSa15040prod/sing_android-playstore-sa_15040-prod-.*.apk")
					break
				case "QA_SA15312_INDIAN_LANG_BATCH1_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa15312indianLangBatch1builds_SingAndroidQaSa15312indianLangBatch1pr/sing_android-playstore-SA_15312_indian_lang_batch1-prod-.*.apk")
					break
				case "QA_NO_SKIP_TOPIC_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_NoSkipTopicBuilds_SingAndroidQaNoSkipTopicProd/sing_android-playstore-no_skip_topic-prod-.*.apk")
					break
				case "QA_SOFT_TRIALS_CLIENT_DEV_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_SoftTrialsClientDevBuilds_SingAndroidQaSoftTrialsClientDevInt/sing_android-playstore-soft_trials_client_dev-int-.*.apk")
					break
				case "QA_SA_14894_HOT_INVITES_BY_SONG_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_Sa14894HotInvitesBySongBuilds_SingAndroidQaSa14894HotInvitesBySongPr/sing_android-playstore-SA_14894_Hot_Invites_By_Song-prod-.*.apk")
					break
				case "QA_HTTPS_V2_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_HttpsV2builds_SingAndroidQaHttpsV2prod/sing_android-playstore-https_v2-prod-.*.apk")
					break
				case "QA_FREEFORM_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingAndroid_FreeformBuilds_SingAndroidQaFreeformProd/sing_android-playstore-freeform-prod-.*.apk")
					break
				default:
					throw new RuntimeException("Unknown env: ${_env}");
					break
			}
		}
	}
	
	@Override
	protected void customPrepareForiOS(params) {
		context.echo "TestRunner->prepareForiOS"
		
		def zafira_project = params.get("zafira_project")
		def _env = params.get("env")
		
		if ("SING".equals(zafira_project)) {
			context.echo "ENV: ${_env}"
			switch(_env) {
				case "DEV_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationBeta/sing-enterprise_dev-qa_automation-beta-.*${build}.*.ipa")
					break
				case "DEV_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationInt/sing-enterprise_dev-qa_automation-int-.*${build}.*.ipa")
					break
				case "DEV_PROD":
					goalMap.put("capabilities.app", "s3://SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationProd/sing-enterprise_dev-qa_automation-prod-.*${build}.*.ipa")
					break
				case "DEV_STG":
					goalMap.put("capabilities.app", "s3://SingIos_QaAutomationBuilds_SingEnterpriseDistQaAutomationStg/sing-enterprise_dev-qa_automation-stg-.*${build}.*.ipa")
					break
				case "UI_AUTOMATION_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaAutomationBeta/sing-ui_automation-qa_automation-beta-.*${build}.*.ipa")
					break
				case "UI_AUTOMATION_RELEASE_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaReleaseBeta/sing-ui_automation-qa_release-beta-.*${build}.*.ipa")
					break
				case "UI_AUTOMATION_FASTDESIGN_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_QaAutomationBuilds_SingUiAutomationQaFastDesignBeta/sing-ui_automation-qa_fast_design-prod-.*${build}.*.ipa")
					break
				case "IOS_MASTER_DEV_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_MasterBuilds_SingEnterpriseDevMasterInt/sing-enterprise_dev-master-int-.*${build}.*.ipa")
					break
				case "IOS_MASTER_BETA":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterBeta/sing-enterprise_dist-master-beta-.*${build}.*.ipa")
					break
				case "IOS_MASTER_INT":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterInt/sing-enterprise_dist-master-int-.*${build}.*.ipa")
					break
				case "IOS_MASTER_PROD":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterProd/sing-enterprise_dist-master-prod-.*${build}.*.ipa")
					break
				case "IOS_MASTER_STG":
					goalMap.put("capabilities.app", "s3://smule.qaprosoft.com/SingIos_EnterpriseDist_SingEnterpriseDistMasterStg/sing-enterprise_dist-master-stg-.*${build}.*.ipa")
					break
				default:
					throw new RuntimeException("Unknown env: ${_env}");
					break
			}
		}
	}
}
