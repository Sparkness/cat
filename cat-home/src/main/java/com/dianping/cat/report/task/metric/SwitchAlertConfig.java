package com.dianping.cat.report.task.metric;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.unidal.tuple.Pair;

import com.dianping.cat.advanced.metric.config.entity.MetricItemConfig;
import com.dianping.cat.consumer.company.model.entity.ProductLine;
import com.dianping.cat.core.dal.Project;
import com.dianping.cat.home.monitorrules.entity.Condition;
import com.dianping.cat.home.monitorrules.entity.Config;
import com.dianping.cat.home.monitorrules.entity.Subcondition;

public class SwitchAlertConfig {

	private DecimalFormat m_df = new DecimalFormat("0.0");

	private static final Long ONE_MINUTE_MILLSEC = 60000L;

	private static final int JUDGE_DEFAULT_MINUTE = 3;

	public List<String> buildExceptionSMSReceivers(ProductLine productLine) {
		List<String> phones = new ArrayList<String>();

		phones.add("18662513308");
		return phones;
	}

	public List<String> buildMailReceivers(ProductLine productLine) {
		List<String> emails = new ArrayList<String>();
		//String emailList = productLine.getEmail();

		emails.add("leon.li@dianping.com");
		//emails.addAll(Splitters.by(",").noEmptyItem().split(emailList));
		return emails;
	}

	public List<String> buildMailReceivers(Project project) {
		List<String> emails = new ArrayList<String>();
		//String emailList = project.getEmail();

		emails.add("leon.li@dianping.com");
		//emails.addAll(Splitters.by(",").noEmptyItem().split(emailList));
		return emails;
	}

	public String buildMailTitle(ProductLine productLine, MetricItemConfig config) {
		StringBuilder sb = new StringBuilder();

		sb.append("[业务告警] [产品线 ").append(productLine.getTitle()).append("]");
		sb.append("[业务指标 ").append(config.getTitle()).append("]");
		return sb.toString();
	}

	public List<String> buildSMSReceivers(ProductLine productLine) {
		List<String> phones = new ArrayList<String>();
		//String phonesList = productLine.getPhone();

		phones.add("18662513308");
		//phones.addAll(Splitters.by(",").noEmptyItem().split(phonesList));
		return phones;
	}

	public Pair<Boolean, String> checkData(MetricItemConfig config, double[] value, double[] baseline, MetricType type,
	      List<Config> configs) {
		for (Config con : configs) {
			int dataLength = queryMaxMinute(con);
			
			double[] validVal = getLastMinutes(value, dataLength);
			double[] validBase = getLastMinutes(baseline, dataLength);
			Pair<Boolean, String> tmpResult = checkDataByConfig(config, validVal, validBase, type, con);
			if (tmpResult.getKey() == true) {
				return tmpResult;
			}
		}

		return new Pair<Boolean, String>(false, "");
	}

	private int queryMaxMinute(Config con) {
		int maxMinute = 0;
		for (Condition condition : con.getConditions()) {
			int tmpMinute = condition.getMinute();
			if (tmpMinute > maxMinute) {
				maxMinute = tmpMinute;
			}
		}
		return maxMinute;
	}

	private Pair<Boolean, String> checkDataByConfig(MetricItemConfig config, double[] value, double[] baseline,
	      MetricType type, Config con) {
		int length = value.length;
		StringBuilder baselines = new StringBuilder();
		StringBuilder values = new StringBuilder();
		double valueSum = 0;
		double baselineSum = 0;

		for (int i = 0; i < length; i++) {
			baselines.append(m_df.format(baseline[i])).append(" ");
			values.append(m_df.format(value[i])).append(" ");
			valueSum = valueSum + value[i];
			baselineSum = baselineSum + baseline[i];

			if (baseline[i] <= 0) {
				baseline[i] = 100;
				return new Pair<Boolean, String>(false, "");
			}
			if (type == MetricType.COUNT || type == MetricType.SUM) {
				if (!judgeByRule(con, value[i], baseline[i], i, length)) {
					return new Pair<Boolean, String>(false, "");
				}
			}
		}
		double percent = (1 - valueSum / baselineSum) * 100;
		StringBuilder sb = new StringBuilder();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		sb.append("[基线值:").append(baselines.toString()).append("] ");
		sb.append("[实际值:").append(values.toString()).append("] ");
		sb.append("[下降:").append(m_df.format(percent)).append("%").append("]");
		sb.append("[告警时间:").append(sdf.format(new Date()) + "]");
		return new Pair<Boolean, String>(true, sb.toString());
	}

	private double[] getLastMinutes(double[] doubleList, int remainCount) {
		double[] result = new double[remainCount];
		int startIndex = doubleList.length - remainCount;

		for (int i = 0; i < remainCount; i++) {
			result[i] = doubleList[startIndex + i];
		}
		return result;
	}

	private Long getMillsByString(String time) throws Exception {
		String[] times = time.split(":");
		int hour = Integer.parseInt(times[0]);
		int minute = Integer.parseInt(times[1]);
		long result = hour * 60 * 60 * 1000 + minute * 60 * 1000;

		return result;
	}

	private boolean judgeByRule(Config ruleConfig, double value, double baseline, int index, int length) {
		boolean isRuleTriggered = false;

		long ruleStartTime;
		long ruleEndTime;
		long nowTime = (System.currentTimeMillis() + 8 * 60 * 60 * 1000) % (24 * 60 * 60 * 1000);

		try {
			ruleStartTime = getMillsByString(ruleConfig.getStarttime());
			ruleEndTime = getMillsByString(ruleConfig.getEndtime()) + ONE_MINUTE_MILLSEC;
		} catch (Exception ex) {
			ruleStartTime = 0L;
			ruleEndTime = 86400000L;
		}

		if (nowTime < ruleStartTime || nowTime > ruleEndTime) {
			return false;
		}

		for (Condition condition : ruleConfig.getConditions()) {
			if (isRuleTriggered) {
				break;
			}

			int minute = condition.getMinute();

			if (minute == 0) {
				minute = JUDGE_DEFAULT_MINUTE;
			}

			if (index < length - minute) {
				continue;
			}

			boolean isSubRuleTriggered = true;

			for (Subcondition subCondition : condition.getSubconditions()) {
				if (!isSubRuleTriggered) {
					break;
				}

				String ruleType = subCondition.getType();
				int ruleValue = Integer.parseInt(subCondition.getText());
				RuleType rule = RuleType.getByTypeId(ruleType);

				if (rule == null) {
					continue;
				} else {
					isSubRuleTriggered = rule.executeRule(value, baseline, ruleValue);
				}
			}

			if (isSubRuleTriggered) {
				isRuleTriggered = true;
			}
		}

		return isRuleTriggered;
	}

}
