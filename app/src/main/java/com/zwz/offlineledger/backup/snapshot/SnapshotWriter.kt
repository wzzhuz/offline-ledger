package com.zwz.offlineledger.backup.snapshot

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.zwz.offlineledger.backup.BackupExporter
import com.zwz.offlineledger.backup.crypto.SecureBackupCodec
import com.zwz.offlineledger.data.repository.LedgerRepository

data class SnapshotWriteResult(
    val fileName: String,
    val bytes: Int,
    val recordCount: Int,
    val pruned: Int
)

/**
 * 把整库快照写进使用者通过 SAF 授权的目录。
 *
 * 只写成品 .ledgerbak：先写 .tmp，回读校验后才改名；任何一步失败都删掉
 * 临时文件，NAS 同步客户端不会把半截文件当成可恢复的备份。
 */
object SnapshotWriter {

    private const val MIME = "application/octet-stream"

    private fun parentOf(treeUri: Uri): Uri {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
    }

    suspend fun write(
        context: Context,
        repository: LedgerRepository,
        passphrase: CharArray
    ): SnapshotWriteResult {
        val tree = SnapshotPrefs.treeUri(context) ?: error("尚未选择快照目录")
        val treeUri = Uri.parse(tree)
        val snapshot = repository.exportSnapshot()
        val json = BackupExporter.encode(snapshot)
        val blob = SecureBackupCodec.encode(json, passphrase)
        val expected = SnapshotCodec.checksum(blob)
        val resolver = context.contentResolver
        val parent = parentOf(treeUri)
        val finalName = SnapshotCodec.fileName(System.currentTimeMillis(), expected)
        val tempName = finalName + SnapshotCodec.TEMP_SUFFIX
        val tempUri = DocumentsContract.createDocument(resolver, parent, MIME, tempName)
            ?: error("无法在所选目录创建临时文件")
        try {
            resolver.openOutputStream(tempUri)?.use { out ->
                out.write(blob)
                out.flush()
            } ?: error("无法打开临时文件输出流")
            val reread = resolver.openInputStream(tempUri)?.use { it.readBytes() }
                ?: error("无法回读临时文件")
            if (!SnapshotCodec.verify(reread, expected)) {
                error("快照校验失败，临时文件已丢弃")
            }
            DocumentsContract.renameDocument(resolver, tempUri, finalName)
                ?: error("无法把临时文件改名为成品")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            throw e
        }
        return SnapshotWriteResult(
            finalName, blob.size, snapshot.records.size, prune(context, treeUri)
        )
    }

    /** 列出目录里的成品快照，按文件名（含时间戳）倒序。 */
    fun list(context: Context): List<Pair<String, Uri>> {
        val tree = SnapshotPrefs.treeUri(context) ?: return emptyList()
        val treeUri = Uri.parse(tree)
        val resolver = context.contentResolver
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val out = mutableListOf<Pair<String, Uri>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.endsWith(SnapshotCodec.EXTENSION)) {
                    out.add(name to DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                }
            }
        }
        return out.sortedByDescending { it.first }
    }

    private fun prune(context: Context, treeUri: Uri): Int {
        val resolver = context.contentResolver
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val names = mutableListOf<String>()
        val uris = mutableMapOf<String, Uri>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name = c.getString(1) ?: continue
                names.add(name)
                uris[name] = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            }
        }
        var removed = 0
        for (name in SnapshotCodec.pruneCandidates(names)) {
            val uri = uris[name] ?: continue
            if (DocumentsContract.deleteDocument(resolver, uri)) removed++
        }
        return removed
    }
}
