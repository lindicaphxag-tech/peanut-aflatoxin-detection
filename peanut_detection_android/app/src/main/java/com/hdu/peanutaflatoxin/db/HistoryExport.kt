package com.hdu.peanutaflatoxin.db

import org.json.JSONArray
import org.json.JSONObject

object HistoryExport {
    fun serialize(records: List<HistoryRecord>): String {
        val items = JSONArray()
        records.forEach { record ->
            items.put(JSONObject()
                .put("id", record.id)
                .put("timestamp_ms", record.timestamp.toLongOrNull() ?: JSONObject.NULL)
                .put("demo_score", record.confidence)
                .put("model_sha256", record.modelSha256 ?: JSONObject.NULL)
                .put("scaler_sha256", record.scalerSha256 ?: JSONObject.NULL)
                .put("pipeline_version", record.pipelineVersion ?: JSONObject.NULL)
                .put("input_sha256", record.inputSha256 ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("schema", "peanut-demo-history-v1")
            .put("validation_status", "unvalidated_demo")
            .put("input_hash_definition", "sha256 of 256x256 grayscale ARGB pixels, row-major, big-endian")
            .put("records", items)
            .toString(2)
    }
}
