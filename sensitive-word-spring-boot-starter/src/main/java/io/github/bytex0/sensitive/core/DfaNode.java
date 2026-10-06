package io.github.bytex0.sensitive.core;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 原DFA节点(DfaNode)保留独立构建树的 API；节点可变，应由调用方线程封闭使用。
 *
 * @author linshiqiang
 * @since 2026-10-06 08:51:10
 */
@Getter
@Setter
public class DfaNode {

    /**
     * 原可写子节点映射，不属于已编译过滤器的共享状态。
     */
    private final Map<Character, DfaNode> children = new HashMap<>();

    /**
     * 是否为词条结尾，默认 false。
     */
    private boolean end;

    /**
     * 结尾节点的分类，可为空。
     */
    private String category;

    /**
     * 结尾节点的完整词条，可为空。
     */
    private String word;

    /**
     * 查询子节点。
     *
     * @param character 字符
     * @return 子节点，不存在时为 null
     */
    public DfaNode getChild(char character) {
        return children.get(character);
    }

    /**
     * 设置子节点。
     *
     * @param character 字符
     * @param node 节点
     */
    public void addChild(char character, DfaNode node) {
        children.put(character, node);
    }

    /**
     * 判断是否存在子节点。
     *
     * @param character 字符
     * @return 是否存在
     */
    public boolean hasChild(char character) {
        return children.containsKey(character);
    }
}
