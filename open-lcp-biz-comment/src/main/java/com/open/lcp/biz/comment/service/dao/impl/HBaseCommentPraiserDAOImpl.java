package com.open.lcp.biz.comment.service.dao.impl;

import org.apache.hadoop.hbase.util.Bytes;

import com.open.lcp.biz.comment.service.dao.HBaseCommentPraiserDAO;

public class HBaseCommentPraiserDAOImpl implements HBaseCommentPraiserDAO {

	/**
	 * 评论点赞表
	 */
	private static final byte[] TABLE_COMMENT_PRAISER = Bytes.toBytes("commentPraiser");
}
