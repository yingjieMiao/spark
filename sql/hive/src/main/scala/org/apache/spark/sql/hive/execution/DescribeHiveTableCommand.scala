/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.hive.execution

import scala.collection.JavaConversions._

import org.apache.hadoop.hive.metastore.api.FieldSchema

import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.expressions.{Attribute, Row}
import org.apache.spark.sql.execution.{Command, LeafNode}
import org.apache.spark.sql.hive.{HiveContext, MetastoreRelation}

/**
 * Implementation for "describe [extended] table".
 *
 * :: DeveloperApi ::
 */
@DeveloperApi
case class DescribeHiveTableCommand(
    table: MetastoreRelation,
    output: Seq[Attribute],
    isExtended: Boolean)(
    @transient context: HiveContext)
  extends LeafNode with Command {

  // Strings with the format like Hive. It is used for result comparison in our unit tests.
  lazy val hiveString: Seq[String] = sideEffectResult.map {
    case Row(name: String, dataType: String, comment) =>
      Seq(name, dataType, Option(comment.asInstanceOf[String]).getOrElse("None"))
        .map(s => String.format(s"%-20s", s))
        .mkString("\t")
  }

  override protected[sql] lazy val sideEffectResult: Seq[Row] = {
    // Trying to mimic the format of Hive's output. But not exactly the same.
    var results: Seq[(String, String, String)] = Nil

    val columns: Seq[FieldSchema] = table.hiveQlTable.getCols
    val partitionColumns: Seq[FieldSchema] = table.hiveQlTable.getPartCols
    results ++= columns.map(field => (field.getName, field.getType, field.getComment))
    if (partitionColumns.nonEmpty) {
      val partColumnInfo =
        partitionColumns.map(field => (field.getName, field.getType, field.getComment))
      results ++=
        partColumnInfo ++
          Seq(("# Partition Information", "", "")) ++
          Seq((s"# ${output.get(0).name}", output.get(1).name, output.get(2).name)) ++
          partColumnInfo
    }

    if (isExtended) {
      results ++= Seq(("Detailed Table Information", table.hiveQlTable.getTTable.toString, ""))
    }

    results.map { case (name, dataType, comment) =>
      Row(name, dataType, comment)
    }
  }

  override def otherCopyArgs = context :: Nil
}
