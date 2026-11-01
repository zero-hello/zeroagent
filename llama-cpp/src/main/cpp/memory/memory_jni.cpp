#include "../jni_util.h"
#include "sqlite-vec.h"
#include "sqlite3.h"
#include <algorithm>
#include <cmath>
#include <jni.h>
#include <memory>
#include <stdexcept>
#include <string>
namespace {
struct Store {
  sqlite3 *db = nullptr;
  int dim = 0;
  ~Store() {
    if (db)
      sqlite3_close_v2(db);
  }
};
struct Statement {
  sqlite3_stmt *p = nullptr;
  ~Statement() { sqlite3_finalize(p); }
};
void check(Store *s, int rc) {
  if (rc != SQLITE_OK && rc != SQLITE_DONE && rc != SQLITE_ROW)
    throw std::runtime_error(sqlite3_errmsg(s->db));
}
void exec(Store *s, const char *sql) {
  check(s, sqlite3_exec(s->db, sql, nullptr, nullptr, nullptr));
}
std::string text(JNIEnv *e, jstring v) {
  if (!v)
    return "";
  const char *p = e->GetStringUTFChars(v, nullptr);
  if (!p)
    throw std::runtime_error("String allocation failed");
  struct Release {
    JNIEnv *env;
    jstring value;
    const char *chars;
    ~Release() { env->ReleaseStringUTFChars(value, chars); }
  } release{e, v, p};
  return std::string(p);
}
void bind(Store *s, sqlite3_stmt *p, int n, const std::string &v) {
  check(s, sqlite3_bind_text(p, n, v.data(), v.size(), SQLITE_TRANSIENT));
}
void prepare(Store *s, Statement &st, const char *sql) {
  check(s, sqlite3_prepare_v2(s->db, sql, -1, &st.p, nullptr));
}
void vector(JNIEnv *e, Store *s, sqlite3_stmt *p, int n, jfloatArray a) {
  if (!a || e->GetArrayLength(a) != s->dim)
    throw std::runtime_error("Embedding dimension mismatch");
  jfloat *v = e->GetFloatArrayElements(a, nullptr);
  if (!v)
    throw std::runtime_error("Vector allocation failed");
  bool ok = true;
  double norm = 0;
  for (int i = 0; i < s->dim; i++) {
    ok = ok && std::isfinite(v[i]);
    norm += static_cast<double>(v[i]) * static_cast<double>(v[i]);
  }
  int rc = SQLITE_OK;
  if (ok && std::isfinite(norm) && norm > 1e-12)
    rc = sqlite3_bind_blob(p, n, v, s->dim * sizeof(float), SQLITE_TRANSIENT);
  e->ReleaseFloatArrayElements(a, v, JNI_ABORT);
  if (!ok || !std::isfinite(norm) || norm <= 1e-12)
    throw std::runtime_error("Invalid embedding");
  check(s, rc);
}
void fail(JNIEnv *e, const std::exception &x) {
  if (e->ExceptionCheck())
    return;
  auto type = e->FindClass("java/lang/IllegalStateException");
  if (type)
    e->ThrowNew(type, x.what());
}
Store *store(jlong h) {
  if (!h)
    throw std::runtime_error("Memory store is closed");
  return reinterpret_cast<Store *>(h);
}
} // namespace
#define NAME(x) Java_me_rerere_zeroagent_data_memory_MemoryNative_##x
extern "C" JNIEXPORT jlong JNICALL NAME(open)(JNIEnv *e, jobject, jstring path,
                                              jint dim, jstring fingerprint) {
  try {
    if (dim < 1 || dim > 4096)
      throw std::runtime_error("Invalid vector dimensions");
    auto s = std::make_unique<Store>();
    s->dim = dim;
    auto p = text(e, path);
    check(s.get(), sqlite3_open_v2(p.c_str(), &s->db,
                                   SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE |
                                       SQLITE_OPEN_FULLMUTEX,
                                   nullptr));
    sqlite3_busy_timeout(s->db, 3000);
    char *error = nullptr;
    int rc = sqlite3_vec_init(s->db, &error, nullptr);
    std::string why = error ? error : "";
    sqlite3_free(error);
    if (rc != SQLITE_OK)
      throw std::runtime_error(why);
    exec(s.get(),
         "PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL; PRAGMA "
         "foreign_keys=ON; CREATE TABLE IF NOT EXISTS metadata (key TEXT "
         "PRIMARY KEY,value TEXT NOT NULL); CREATE TABLE IF NOT EXISTS "
         "memories(id INTEGER PRIMARY KEY,external_id TEXT NOT NULL "
         "UNIQUE,raw_text TEXT NOT NULL,vector_blob BLOB NOT NULL,timestamp "
         "INTEGER NOT NULL,mood_tag TEXT NOT NULL,mood_blob "
         "BLOB,conversation_id TEXT NOT NULL,role TEXT NOT NULL); CREATE INDEX "
         "IF NOT EXISTS memory_time ON memories(timestamp DESC);");
    Statement st;
    prepare(s.get(), st, "SELECT value FROM metadata WHERE key='model'");
    rc = sqlite3_step(st.p);
    auto key = text(e, fingerprint) + ":" + std::to_string(dim);
    if (rc == SQLITE_ROW) {
      if (key != reinterpret_cast<const char *>(sqlite3_column_text(st.p, 0)))
        throw std::runtime_error("Memory model changed; export or remove the "
                                 "old index before rebuilding");
    } else {
      check(s.get(), rc);
      Statement ins;
      prepare(s.get(), ins,
              "INSERT INTO metadata(key,value) VALUES('model',?)");
      bind(s.get(), ins.p, 1, key);
      check(s.get(), sqlite3_step(ins.p));
    }
    exec(s.get(), ("CREATE VIRTUAL TABLE IF NOT EXISTS memory_vectors USING "
                   "vec0(embedding float[" +
                   std::to_string(dim) + "] distance_metric=cosine)")
                      .c_str());
    return reinterpret_cast<jlong>(s.release());
  } catch (const std::exception &x) {
    fail(e, x);
    return 0;
  }
}
extern "C" JNIEXPORT void JNICALL NAME(close)(JNIEnv *, jobject, jlong h) {
  delete reinterpret_cast<Store *>(h);
}
extern "C" JNIEXPORT void JNICALL NAME(put)(JNIEnv *e, jobject, jlong h,
                                            jstring id, jbyteArray raw,
                                            jfloatArray embedding,
                                            jlong timestamp, jstring mood,
                                            jstring conversation,
                                            jstring role) {
  try {
    auto *s = store(h);
    exec(s, "BEGIN IMMEDIATE");
    try {
      Statement st;
      prepare(s, st,
              "INSERT INTO "
              "memories(external_id,raw_text,vector_blob,timestamp,mood_tag,"
              "conversation_id,role) VALUES(?,?,?,?,?,?,?) ON "
              "CONFLICT(external_id) DO NOTHING RETURNING id");
      bind(s, st.p, 1, text(e, id));
      bind(s, st.p, 2, byteArrayToUtf8(e, raw));
      vector(e, s, st.p, 3, embedding);
      check(s, sqlite3_bind_int64(st.p, 4, timestamp));
      bind(s, st.p, 5, text(e, mood));
      bind(s, st.p, 6, text(e, conversation));
      bind(s, st.p, 7, text(e, role));
      int rc = sqlite3_step(st.p);
      check(s, rc);
      if (rc == SQLITE_ROW) {
        auto row = sqlite3_column_int64(st.p, 0);
        Statement v;
        prepare(s, v,
                "INSERT INTO memory_vectors(rowid,embedding) VALUES(?,?)");
        check(s, sqlite3_bind_int64(v.p, 1, row));
        vector(e, s, v.p, 2, embedding);
        check(s, sqlite3_step(v.p));
        check(s, sqlite3_step(st.p));
      }
      exec(s,
           "DELETE FROM memory_vectors WHERE rowid IN (SELECT id FROM memories "
           "ORDER BY timestamp DESC,id DESC LIMIT -1 OFFSET 10000); DELETE "
           "FROM memories WHERE id IN (SELECT id FROM memories ORDER BY "
           "timestamp DESC,id DESC LIMIT -1 OFFSET 10000);");
      exec(s, "COMMIT");
    } catch (...) {
      sqlite3_exec(s->db, "ROLLBACK", nullptr, nullptr, nullptr);
      throw;
    }
  } catch (const std::exception &x) {
    fail(e, x);
  }
}
extern "C" JNIEXPORT jbyteArray JNICALL NAME(query)(JNIEnv *e, jobject, jlong h,
                                                    jfloatArray embedding,
                                                    jint k) {
  try {
    auto *s = store(h);
    Statement st;
    prepare(s, st,
            "SELECT "
            "coalesce(json_group_array(json_object('id',m.id,'external_id',m."
            "external_id,'text',m.raw_text,"
            "'timestamp',m.timestamp,'mood',m.mood_tag,'conversation',m."
            "conversation_id,'role',m.role,'distance',v.distance)),'[]') FROM "
            "(SELECT rowid,distance FROM memory_vectors WHERE embedding MATCH "
            "? AND k=? ORDER BY distance) v JOIN memories m ON m.id=v.rowid");
    vector(e, s, st.p, 1, embedding);
    check(s, sqlite3_bind_int(st.p, 2, std::max(1, std::min(512, (int)k))));
    check(s, sqlite3_step(st.p));
    return utf8ToByteArray(e, std::string(reinterpret_cast<const char *>(
                                              sqlite3_column_text(st.p, 0)),
                                          sqlite3_column_bytes(st.p, 0)));
  } catch (const std::exception &x) {
    fail(e, x);
    return nullptr;
  }
}
extern "C" JNIEXPORT jbyteArray JNICALL NAME(recent)(JNIEnv *e, jobject,
                                                     jlong h, jint limit) {
  try {
    auto *s = store(h);
    Statement st;
    prepare(
        s, st,
        "SELECT "
        "coalesce(json_group_array(json_object('id',id,'external_id',external_"
        "id,'text',raw_text,'"
        "timestamp',timestamp,'mood',mood_tag,'conversation',conversation_id,'"
        "role',role,'vector',json(vec_to_json(vector_blob)))),'[]') FROM "
        "(SELECT * FROM memories ORDER BY timestamp DESC,id DESC LIMIT ?)");
    check(s, sqlite3_bind_int(st.p, 1, std::max(1, std::min(200, (int)limit))));
    check(s, sqlite3_step(st.p));
    return utf8ToByteArray(e, std::string(reinterpret_cast<const char *>(
                                              sqlite3_column_text(st.p, 0)),
                                          sqlite3_column_bytes(st.p, 0)));
  } catch (const std::exception &x) {
    fail(e, x);
    return nullptr;
  }
}
extern "C" JNIEXPORT void JNICALL NAME(remove)(JNIEnv *e, jobject, jlong h,
                                               jlong id) {
  try {
    auto *s = store(h);
    exec(s, "BEGIN IMMEDIATE");
    try {
      for (auto sql : {"DELETE FROM memory_vectors WHERE rowid=?",
                       "DELETE FROM memories WHERE id=?"}) {
        Statement st;
        prepare(s, st, sql);
        check(s, sqlite3_bind_int64(st.p, 1, id));
        check(s, sqlite3_step(st.p));
      }
      exec(s, "COMMIT");
    } catch (...) {
      sqlite3_exec(s->db, "ROLLBACK", nullptr, nullptr, nullptr);
      throw;
    }
  } catch (const std::exception &x) {
    fail(e, x);
  }
}

extern "C" JNIEXPORT jboolean JNICALL NAME(contains)(JNIEnv *e, jobject,
                                                     jlong h, jstring id) {
  try {
    auto *s = store(h);
    Statement st;
    prepare(s, st, "SELECT 1 FROM memories WHERE external_id=?");
    bind(s, st.p, 1, text(e, id));
    int rc = sqlite3_step(st.p);
    check(s, rc);
    return rc == SQLITE_ROW;
  } catch (const std::exception &x) {
    fail(e, x);
    return false;
  }
}
