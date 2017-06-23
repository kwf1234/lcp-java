package com.open.lcp.passport.api;

import java.util.List;

import com.open.common.enums.Gender;
import com.open.lcp.passport.UserAccountType;
import com.open.lcp.passport.dto.PassportOAuthAccountDTO;
import com.open.lcp.passport.dto.PassportUserAccountDTO;
import com.open.lcp.passport.dto.RequestUploadAvatarResultDTO;

public interface AccountBizApi {

	public PassportUserAccountDTO getUserInfoByUserId(Long userId);

	public List<PassportOAuthAccountDTO> getOAuthAccountListByUserId(Long userId);

	public int unbindAccount(Long userId, UserAccountType userAccountType);

	public boolean updateGender(Long userId, Gender gender);

	public boolean updateNickName(Long userId, String nickName);

	public boolean updateDescription(Long userId, String description);

	public RequestUploadAvatarResultDTO requestUploadAvatar(String prefix, Long userId);

	public String commitUploadAvatar(String prefix, Long userId);

	public UserAccountType getUserType(Long userId);
}
