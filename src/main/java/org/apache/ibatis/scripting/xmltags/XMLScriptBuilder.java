/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Clinton Begin
 */
// XML 动态SQL 构建器 负责将Sql 解析成 SqlSource 对象
public class XMLScriptBuilder extends BaseBuilder {

  // 当前的 SqL 的 XNode 对象
  private final XNode context;
  // 是否为动态sql
  private boolean isDynamic;
  // sql 参数类型
  private final Class<?> parameterType;
  // NodeHandler 映射
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
    // 初始化 NodeHandler
    initNodeHandlerMap();
  }


  // 每一个标签都有一个专属的 NodeHandler 实现类
  private void initNodeHandlerMap() {
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }

  // 将 sql 解析成 SqlNode 对象
  public SqlSource parseScriptNode() {
    // 解析 Sql
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource;
    // 根据 是否为动态Sql 返回 DynamicSqlSource 还是 RawSqlSource
    if (isDynamic) {
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  // 将 SQL 解析成 MixedSqlNode
  protected MixedSqlNode parseDynamicTags(XNode node) {
    // 创建一个 Sql 数组 每个子节点就是一个SqlNode 对象
    List<SqlNode> contents = new ArrayList<>();
    //获取 SQl所有子节点
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      // 如果类型是 Node CDATA_SECTION_NODE 或者是 TEXT_NODE
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        // 获取结点内容
        String data = child.getStringBody("");
        // 创建一个新的 TestSqlNode 对象
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // 判断是否为动态的
        if (textSqlNode.isDynamic()) {
          // 如果是动态的 添加到 context 并且 标志为动态
          contents.add(textSqlNode);
          isDynamic = true;
        } else {
          // 如果不是动态的，创建 StaticTextSqlNode 对象 并且添加到 contents
          contents.add(new StaticTextSqlNode(data));
        }
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628
        // 如果是 ELEMENT_NODE
        String nodeName = child.getNode().getNodeName();
        // 获取对应的 NodeHandler 对象
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // 执行 Nodehandler 处理
        handler.handleNode(child, contents);
        isDynamic = true;
      }
    }
    // 返回 MixedSqlNode 对象
    return new MixedSqlNode(contents);
  }

  // 在 XMLScriptBuilder 内部中有相应的实现类
  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  // 实现了 NodeHandler ，专门处理 <bind/>
  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 获取 name
      final String name = nodeToHandle.getStringAttribute("name");
      // 获取 value
      final String expression = nodeToHandle.getStringAttribute("value");
      // 构建一个 VarDeclSqlNode 对象
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      // 添加到 SqlNode 数组中
      targetContents.add(node);
    }
  }

  // 实现了 NodeHandler ，专门处理 <trim/>
  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的Sql结点，返回混合的SqlNode  MixedSqlNode
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取 prefix prefixOverrides suffix suffixOverrides 标签
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      // 构建一个 TrimSqlNode 并添加到 SqlNode 数组中
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }
  // 实现 NodeHandler ，专门处理 <where/> 标签
  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 还是先解析 内部的 sql 结点，返回一个 MixedSqlNode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 构建一个 WhereSqlNode 对象，并添加到 SqlNode 数组中
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  // 实现 NodeHandler ，专门处理 <set/> 标签
  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 参考 where 解析逻辑
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      targetContents.add(set);
    }
  }

  // 实现 nodehandler ，专门处理 <foreach/> 标签
  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 sql 结点，返回 mixedsqlnode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取 collection item index open close separator 属性
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      // 构建一个 ForEachSqlNode 对象，并添加到 targetContents
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      targetContents.add(forEachSqlNode);
    }
  }

  // 实现 nodehandler ，专门处理 <if/> 标签
  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析内部的 sql 结点，返回 mixedsqlnode 对象
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取 test 属性
      String test = nodeToHandle.getStringAttribute("test");
      // 构建IfSqlNode对象，并添加到targetContents
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      targetContents.add(ifSqlNode);
    }
  }

  // 实现 nodehandler ，专门处理 <otherwise/> 标签
  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  // 实现 nodehandler ，专门处理 <choose/> 标签
  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 创建一个存放条件的 SqlNode 数组
      List<SqlNode> whenSqlNodes = new ArrayList<>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
      // 解析 when otherwise 结点
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      // 获取 otherwise 结点
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      // 构造 ChooseSqlNode 对象，并添加到targetContents
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      targetContents.add(chooseSqlNode);
    }

    // 处理 when 和 otherwise 结点
    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        // 获取相应的处理器
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        // 如果之类是 ifHander 那么就是走 when 的逻辑
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          // otherwise 的情况
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
