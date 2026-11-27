package me.rerere.zeroagent.ui.components.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import me.rerere.zeroagent.ui.components.table.DataTable
import me.rerere.zeroagent.ui.theme.JetbrainsMono

internal val MarkdownCodeBlockLayout = Modifier.fillMaxWidth().padding(bottom = 4.dp)

@Composable
internal fun MarkdownImage(model: String, contentDescription: String?) {
    ZoomableAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .widthIn(min = 120.dp)
            .heightIn(min = 120.dp),
    )
}

@Composable
internal fun MarkdownTable(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    outerBorder: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape: Shape = MaterialTheme.shapes.small,
) {
    DataTable(
        headers = headers,
        rows = rows,
        modifier = modifier,
        columnMinWidths = List(headers.size) { 80.dp },
        columnMaxWidths = List(headers.size) { 200.dp },
        outerBorder = outerBorder,
        shape = shape,
    )
}

internal fun markdownLinkStyle(colorScheme: ColorScheme) = SpanStyle(
    color = colorScheme.primary,
    textDecoration = TextDecoration.Underline,
)

internal fun AnnotatedString.Builder.withMarkdownLink(
    destination: String,
    style: SpanStyle,
    content: AnnotatedString.Builder.() -> Unit,
) {
    withLink(LinkAnnotation.Url(destination)) {
        withStyle(style, content)
    }
}

internal fun AnnotatedString.Builder.appendMarkdownCitation(
    domain: String,
    id: String,
    inlineContents: MutableMap<String, InlineTextContent>,
    colorScheme: ColorScheme,
    onClickCitation: (String) -> Unit,
) {
    if (id.length != 6) return
    val key = "citation:$id"
    inlineContents.putIfAbsent(
        key,
        InlineTextContent(
            placeholder = Placeholder(
                width = (domain.length * 7).sp,
                height = 1.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
            children = {
                Box(
                    modifier = Modifier
                        .clickable { onClickCitation(id.trim()) }
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.tertiaryContainer.copy(0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = domain,
                        modifier = Modifier.wrapContentSize(),
                        style = TextStyle(
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            fontFamily = JetbrainsMono,
                            color = colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Thin,
                        ),
                    )
                }
            },
        ),
    )
    appendInlineContent(key)
}
