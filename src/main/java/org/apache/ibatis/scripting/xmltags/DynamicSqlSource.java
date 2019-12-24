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

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// 动态sql source
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  // sqlNode 对象
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建 DynamicContext
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // rootSqlNode 应用
    rootSqlNode.apply(context);
    //  创建 SqlSourceBuilder 对象
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 解析出 SqlSource 对象  StaticSqlSource 类。 这个过程，会将 #{} 对，转换成对应的 ? 占位符，并获取该占位符对应的 ParameterMapping 对象。
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // 获得 BoundSql 对象
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 添加附加参数到 BoundSql 对象中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    // 返回 BoundSql 对象
    return boundSql;
  }

}
