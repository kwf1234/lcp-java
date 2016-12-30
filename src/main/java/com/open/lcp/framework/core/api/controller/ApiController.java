package com.open.lcp.framework.core.api.controller;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.open.dbs.cache.SSDBCounterByThread;
import com.open.lcp.framework.core.api.LcpThreadLocal;
import com.open.lcp.framework.core.api.command.ApiCommand;
import com.open.lcp.framework.core.api.command.ApiCommandContext;
import com.open.lcp.framework.core.api.command.RequestBaseContext;
import com.open.lcp.framework.core.api.service.ApiCommandLookupService;
import com.open.lcp.framework.core.api.service.dao.info.AppInfo;
import com.open.lcp.framework.core.consts.HttpConstants;
import com.open.lcp.framework.core.consts.LcpConstants;
import com.open.lcp.framework.core.facade.ApiResult;
import com.open.lcp.framework.core.facade.ApiResultCode;
import com.open.lcp.framework.security.service.UserAccountService;
import com.open.lcp.framework.security.service.UserAccountService.UserType;
import com.open.lcp.framework.util.LcpUtils;

@Controller
@RequestMapping(method = { RequestMethod.GET, RequestMethod.POST })
public class ApiController {
	static {
		SSDBCounterByThread.enable();
	}
	/**
	 * Logger for this class
	 */
	private static final Log logger = LogFactory.getLog(ApiController.class);
	private static final Log httpAccessLogger = LogFactory.getLog("http_access");
	private static final Log httpAccessNZLogger = LogFactory.getLog("http_access_nz");
	private static final Log httpAccessSlowLogger = LogFactory.getLog("http_access_slow");
	private static final Log httpAccessSlowXLogger = LogFactory.getLog("http_access_slowX");
	private static final Log httpAccessSlowXXLogger = LogFactory.getLog("http_access_slowXX");

	private static final long COST_TIME_LIMIT = 1000;
	private static final long COST_TIME_LIMIT_X = 10000;
	private static final long COST_TIME_LIMIT_XX = 100000;
	private static final String httpAccessLogFormat = "%s|%s|%s|%s|%s|%s|%s|%s|%s";
	public static final ApiResult ERR_REQUIRED_PARAM = new ApiResult(ApiResultCode.E_SYS_PARAM);
	public static final ApiResult ERR_SYS_PARAM = new ApiResult(ApiResultCode.E_SYS_PARAM);
	@Autowired
	private MobileClientAppService mobileClientAppService;
	@Autowired
	private ApiCommandLookupService commandLookupService;
	@Autowired
	private AccountApi accountApi;

	@Autowired
	private UserAccountService userAccountService;

	@RequestMapping("/api/**")
	public ApiResult apiV1() {
		return api(1);
	}

	@RequestMapping("/api2/**")
	public ApiResult apiV2() {
		return api(2);
	}

	private ApiResult api(final int apiV) {
		final HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		long curTime = System.currentTimeMillis();
		long userIdLog = 0;
		String clientIp = "";
		int clientPort = 0;
		// String serverIpLog = "";
		String userAgentLog = "";
		String methodNameLog = "";
		String urlFull = "";
		// String sig = "";
		ApiResult apiResult = new ApiResult();
		ApiCommandContext context = null;
		try {
			{// 统计相关参数
				// InetAddress addr = InetAddress.getLocalHost();
				// serverIpLog = addr.getHostAddress().toString();
				clientIp = LcpUtils.getRemoteAddr(request);
				clientPort = LcpUtils.getRemotePort(request);
				userAgentLog = request.getHeader("User-Agent");
			}
			// 构造参数
			final String httpMethod = request.getMethod().toUpperCase();
			final String httpReqURI = request.getRequestURI();
			final RequestBaseContext requestBaseContext = new RequestBaseContext(apiV, curTime, httpMethod, httpReqURI,
					clientIp);
			byte[] btsBody = null;
			final String contentType = request.getContentType();
			final int contentLength = request.getContentLength();
			if (contentType != null && contentType.equalsIgnoreCase("application/octet-stream") && contentLength > 0) {
				btsBody = new byte[request.getContentLength()];
				int c = 0, index = 0;
				ServletInputStream in = request.getInputStream();
				while ((c = in.read(btsBody, index, btsBody.length - index)) > 0)
					index += c;
				if (btsBody.length != index) {// 出错
					logger.info(String.format("octet-stream error of %s, loaded %s, not content length %s",
							request.getRequestURI(), index, btsBody.length));
					btsBody = null;
					return ERR_REQUIRED_PARAM;
				}
			}
			final Map<String, String> requestParamMap = LcpUtils.fillParamMap(request);
			if (btsBody != null && btsBody.length > 0) {// 有postbody时的处理
				final String byteBodyHash = DigestUtils.md5Hex(btsBody).toLowerCase();
				// String byteBodyHash =
				// HexUtil.byteArrayToHexString(DigestUtils.md5(btsBody)).toLowerCase();
				final String oldOctet = requestParamMap.get(LcpConstants.PARAM_OCTET_STREAM);
				if (oldOctet != null) {
					if (!oldOctet.equals(byteBodyHash)) {
						return ERR_SYS_PARAM;
					}
				} else {
					requestParamMap.put(LcpConstants.PARAM_OCTET_STREAM, byteBodyHash);
				}
			}
			request.setAttribute(LcpConstants.REQ_ATTR_PerSMAP, requestParamMap);

			// 解析uri到method名字，优先判断
			String methodName = LcpUtils.getCmdMethodFromURI(requestBaseContext.getRequestURI());
			{
				if (methodName == null) {// URL中没有时，从参数中取一次容错。
					methodName = requestParamMap.get(HttpConstants.PARAM_METHOD);
				}
				methodNameLog = methodName;
				if (StringUtils.isEmpty(methodName)) { // 没有方法名
					apiResult.setCode(ApiResultCode.E_SYS_UNKNOWN_METHOD);
					return apiResult;
				}
			}
			request.setAttribute(LcpConstants.REQ_API_METHOD_NAME, methodName);
			final String queryString = LcpUtils.buildQueryString(requestParamMap);
			if (StringUtils.isNotBlank(queryString)) {
				urlFull = httpReqURI + "?" + queryString;
			} else {
				urlFull = httpReqURI;
			}
			requestBaseContext.setRequestParamMap(requestParamMap);
			final String version = requestParamMap.get(LcpConstants.PARAM_V);
			final ApiCommand apiCommand = commandLookupService.lookupApiCommand(methodName, version);
			if (apiCommand == null) {
				// apiCommand is unknown
				apiResult.setCode(ApiResultCode.E_SYS_UNKNOWN_METHOD);
				return apiResult;
			}
			final Map<String, String> httpHeads = LcpUtils.getHttpHeads(request);
			// 校验平台级参数的合法性
			if (!this.validateBaseRequiredParams(httpHeads, requestBaseContext, apiResult, methodName)) {
				return apiResult;
			}
			// 防重发
			if (!this.validateRequestFrequency(requestBaseContext, apiResult)) {
				return apiResult;
			}
			// 是否需要登录
			userIdLog = requestBaseContext.getUserId();
			if (requestBaseContext.getUserId() == 0 && this.commandLookupService.isNeedLogin(methodName, version)) {
				apiResult.setCode(ApiResultCode.E_SYS_INVALID_TICKET);
				return apiResult;
			}
			// 权限校验
			// TODO：如果需要登录，对user用户身份的检查，例如是否封禁等等
			// TODO：antispam的检查
			// TODO：流量控制
			// 检查客户端app是否对这个方法有权限
			if (!commandLookupService.isOpen(methodName, version)) {
				if (!mobileClientAppService.isAllowedApiMethod(requestBaseContext.getAppInfo().getAppId(), methodName,
						clientIp)) {
					apiResult.setCode(ApiResultCode.E_SYS_PERMISSION_DENY);
					return apiResult;
				}
			}
			// 封装command参数
			Map<String, Object> binaryParams = null;
			if (request instanceof MultipartHttpServletRequest) {
				MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
				binaryParams = new HashMap<String, Object>();
				for (Map.Entry<String, MultipartFile> e : multipartRequest.getFileMap().entrySet()) {
					binaryParams.put(e.getKey(), e.getValue());
				}
			}
			context = new ApiCommandContext(curTime, //
					requestBaseContext.getAppInfo(), //
					requestBaseContext.getRequestParamMap(), //
					binaryParams, //
					requestBaseContext.getTicket(), //
					requestBaseContext.getSecretKey(), //
					methodName, //
					httpHeads, //
					clientIp, //
					clientPort, //
					userAccountService);
			if (btsBody != null) {
				context.setOctetBody(btsBody);
			}
			if (requestBaseContext.getUserId() > 0) {
				context.setUser(requestBaseContext.getUserType(), requestBaseContext.getUserId());
			}
			if (clientPort > 0) {
				context.addStatExt("clientIp", clientIp);
				context.addStatExt("clientPort", clientPort);
			}
			final String blCode = context.getLcpAppInfo().getBlCode();
			if (blCode != null && blCode.length() > 0) {
				context.addStatExt("blCode", blCode);
			}
			context.addStatExt("os", context.getLcpAppInfo().getAppOsId());
			LcpThreadLocal.thCommandContext.set(context);
			apiResult = apiCommand.execute(context);
			return apiResult;
		} catch (MaxUploadSizeExceededException m) {
			logger.info("upload size exceeded", m);
			apiResult = new ApiResult();
			apiResult.setCode(ApiResultCode.E_BIZ_FILE_TOO_LARGE_FILE);
		} catch (NumberFormatException nfe) {
			logger.error("Param parse error", nfe);
			apiResult = new ApiResult();
			apiResult.setCode(ApiResultCode.E_SYS_PARAM);
		} catch (Throwable e) {
			logger.error("CommandController handleRequestInternal", e);
			apiResult = new ApiResult();
			apiResult.setCode(ApiResultCode.E_SYS_UNKNOWN);
		} finally { // log统计
			final long time = System.currentTimeMillis();
			final long costTime = time - curTime;
			final String methodName = StringUtils.defaultString(request.getMethod());
			try {
				final String httpAccessLogMsg = String.format(httpAccessLogFormat//
						, time + "" // 时间
						, urlFull // url
						, userIdLog + "" // userId
						, methodName // http method
						, StringUtils.defaultString(userAgentLog) // http agent
						, StringUtils.defaultString(clientIp) // clientIp
						, StringUtils.defaultString(methodNameLog) // mcpMethodName
						, costTime + "" // 消耗的时间
						, apiResult.getCode() + "");
				httpAccessLogger.error(httpAccessLogMsg);
				// // this.checkServerQuality(costTime);
				if (costTime >= COST_TIME_LIMIT) {
					httpAccessSlowLogger.info(httpAccessLogMsg);
					if (costTime >= COST_TIME_LIMIT_X) {
						httpAccessSlowXLogger.info(httpAccessLogMsg);
						if (costTime >= COST_TIME_LIMIT_XX) {
							httpAccessSlowXXLogger.info(httpAccessLogMsg);
						}
					}
				}
				if (apiResult.getCode() > 0) {
					httpAccessNZLogger.info(httpAccessLogMsg);
				}
				if (context != null) {
					context.addStatExt("costTime", costTime);
					context.addStatExt("ssdb", SSDBCounterByThread.clear());
				}
			} catch (Exception e) {
				logger.error("finally method name:" + methodNameLog, e);
			}
		}
		return apiResult;

	}

	/**
	 * 校验平台基本参数
	 * 
	 * @param requestParamMap
	 * @param response
	 * @param context
	 * @param apiResultHolder
	 * @return
	 * @throws Exception
	 */
	private boolean validateBaseRequiredParams(final Map<String, String> httpHeads,
			RequestBaseContext requestBaseContext, ApiResult apiResult, final String methodName) throws Exception {
		Map<String, String> requestParamMap = requestBaseContext.getRequestParamMap();
		final int appId = NumberUtils.toInt(requestParamMap.get(HttpConstants.PARAM_APP_ID));
		final AppInfo appInfo = mobileClientAppService.getAppInfo(appId);
		// 接入信息无效
		if (appInfo == null) {
			apiResult.setCode(ApiResultCode.E_SYS_INVALID_APP_ID);
			return false;
		}
		requestBaseContext.setAppInfo(appInfo);
		final String sig = requestParamMap.get(HttpConstants.PARAM_SIG);
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("[%s]:{validateBaseRequiredParams:[sig=%s]}", this.getClass().getName(), sig));
		}
		// sig is required
		if (StringUtils.isEmpty(sig)) {
			apiResult.setCode(ApiResultCode.E_SYS_INVALID_SIG);
			return false;
		}
		// t票处理
		final String t = requestParamMap.get(HttpConstants.PARAM_TICKET);
		final String xlt = requestParamMap.get(HttpConstants.PARAM_XUNLEI_TICKET);
		final String xluid = requestParamMap.get(HttpConstants.PARAM_XUNLEI_UID);
		final String version = requestParamMap.get(LcpConstants.PARAM_V);
		requestBaseContext.setTicket(t);
		if (StringUtils.isNotEmpty(t)) {
			CheckTicket ticket = null;
			try {
				ticket = accountApi.validateTicket(t);
			} catch (Exception e) {
				logger.error(String.format("accountApi.validateTicket(%s) sig:%s", t, sig), e); //$NON-NLS-1$
			}
			if (ticket != null && ticket.getXlUserId() != null && ticket.getXlUserId() > 0) {
				requestBaseContext.setUser(UserType.Ra2, ticket.getXlUserId());
				final String userSecretKey = ticket.getUserSecretKey();// TOTO:缺密钥
				if (userSecretKey == null || userSecretKey.isEmpty()) {// 兼容可能偶现的取密钥失败
					requestBaseContext.setSecretKey(null);
				} else {
					requestBaseContext.setSecretKey(requestBaseContext.getAppInfo().getAppSecretKey() + userSecretKey);
				}
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("[%s]:{lookupSecretKey:[.getUserId()=%s][.getSecretKey()=%s]}", //
							this.getClass().getName(), requestBaseContext.getUserId(),
							requestBaseContext.getSecretKey()));
				}
			} else if (this.commandLookupService.isNeedLogin(methodName, version)) {// 需要登录时，报票错误。
				apiResult.setCode(ApiResultCode.E_SYS_INVALID_TICKET);
				return false;
			} else {// 无需登录时，兼容登录信息失效。
				logger.warn(String.format("%s[%s] sig:%s t login failed. anonymity and continue.", methodName, version,
						sig));
			}
		} else if (StringUtils.isNotEmpty(xlt)) {
			Long u = null;
			final String clientIp = requestBaseContext.getClientIp();
			try {
				final String proFileName = XunleiEnvFinder.getProfile();
				if (proFileName.equals("t16")) {
					if (StringUtils.isNotEmpty(xluid)) {
						u = Long.valueOf(xluid);
					}
				} else {
					u = accountApi.validateMobileThunderUser(xluid, xlt, clientIp);
				}

			} catch (Exception e) {
				logger.error(String.format("accountApi.validateMobileThunderUser(%s,%s,%s) sig:%s", //
						xluid, xlt, clientIp, sig), e);
			}
			if (u == null || u == 0) {
				if (this.commandLookupService.isNeedLogin(methodName, version)) {// 必须登录时，抛票错误。
					apiResult.setCode(ApiResultCode.E_SYS_INVALID_TICKET);
					return false;
				} else {// 无需登录时，兼容登录信息失效。
					logger.warn(String.format(
							"%s[%s] accountApi.validateMobileThunderUser(%s,%s,%s) sig:%s xlt login failed. anonymity and continue.", //
							methodName, version, xluid, xlt, clientIp, sig));
				}
			} else {
				final long userId = u;
				requestBaseContext.setUser(UserType.user, userId);
				// requestBaseContext.setTicket(xlt);
			}
		} else { // 无t票时，只用app的secretKey
			if (this.commandLookupService.isNeedLogin(methodName, version)) {
				apiResult.setCode(ApiResultCode.E_SYS_TICKET_NOT_EXIST);
				return false;
			}
			requestBaseContext.setSecretKey(requestBaseContext.getAppInfo().getAppSecretKey());
		}
		final String secretKey = requestBaseContext.getSecretKey();
		if (secretKey != null) {// 成功得到密钥后才验sig值
			String normalizedString = LcpUtils.generateNormalizedString(httpHeads, requestParamMap);
			if (requestBaseContext.getApiV() > 1) {
				normalizedString = requestBaseContext.getRequestURI() + normalizedString;
			}
			String requiredSig = LcpUtils.generateSignature(normalizedString, secretKey);
			if (!StringUtils.equalsIgnoreCase(sig, requiredSig)) {
				if (logger.isDebugEnabled()) {
					logger.debug(String.format("%s: sig[%s] error. requiredSig[%s] normalString:[%s]", methodName, sig,
							requiredSig, normalizedString));
				}
				apiResult.setCode(ApiResultCode.E_SYS_INVALID_SIG);
				return false;
			}
		}
		return true;
	}

	private static WeakReference<ConcurrentHashMap<String, Boolean>> SRF_SIG = new WeakReference<ConcurrentHashMap<String, Boolean>>(
			new ConcurrentHashMap<String, Boolean>());

	/**
	 * 防重发机制, 根据sig判断
	 * 
	 * @param requestParamMap
	 * @param response
	 * @param context
	 * @param apiResultHolder
	 * @return
	 * @throws Exception
	 */
	private boolean validateRequestFrequency(RequestBaseContext requestBaseContext, ApiResult apiResult)
			throws Exception {
		Map<String, String> requestParamMap = requestBaseContext.getRequestParamMap();
		// 防重发机制, 根据sig判断
		final long userId = requestBaseContext.getUserId();
		final String reqSig = requestParamMap.get(HttpConstants.PARAM_SIG);
		final long timeOf10s = System.currentTimeMillis() / 10000;// 十秒为单位
		final String sig = String.format("%s.%s@%s", userId, reqSig, timeOf10s);
		try {
			ConcurrentHashMap<String, Boolean> sigMap = SRF_SIG.get();
			if (sigMap == null) {// 空的时候，直接返回成功
				sigMap = new ConcurrentHashMap<String, Boolean>();
				SRF_SIG = new WeakReference<ConcurrentHashMap<String, Boolean>>(sigMap);
				logger.warn("CommandController.validateRequestFrequency: WeakReference empty.");
			} else {
				Boolean b = sigMap.get(sig);
				if (b != null) { // 是重发的请求，用户访问过于频繁
					apiResult.setCode(ApiResultCode.E_SYS_REQUEST_TOO_FAST);
					logger.warn(String.format("sig repeat: %s, abort.", sig));
					return false;
				}
			}
			sigMap.put(sig, true);
		} catch (Throwable t) {
			logger.warn("validateRequestFrequency", t);
		}
		return true;
	}
}
