package com.open.lcp.biz.comment.service.dao.impl;

import org.apache.hadoop.hbase.util.Bytes;

import com.open.lcp.biz.comment.service.dao.HBaseCommentUserRecordDAO;

public class HBaseCommentUserRecordDAOImpl implements HBaseCommentUserRecordDAO {

	/**
	 * 用户评论记录表
	 */
	private static final byte[] TABLE_COMMENT_USERCOMMENTS = Bytes.toBytes("userComment");
}
