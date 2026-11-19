package com.ark.voidlauncher.data.db

import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo

fun PageEntity.toPageInfo(): PageInfo = PageInfo(
    pageId = pageId,
    coordX = coordX,
    coordY = coordY,
    title = title,
    gridCols = gridCols,
    gridRows = gridRows,
    isSecret = isSecret,
    pageType = pageType,
)

fun PageInfo.toPageEntity(): PageEntity = PageEntity(
    pageId = pageId,
    coordX = coordX,
    coordY = coordY,
    title = title,
    gridCols = gridCols,
    gridRows = gridRows,
    isSecret = isSecret,
    pageType = pageType,
)

fun PageEdgeEntity.toEdgeInfo(): EdgeInfo = EdgeInfo(
    fromPageId = fromPageId,
    swipeDirection = swipeDirection,
    toPageId = toPageId,
)

fun EdgeInfo.toPageEdgeEntity(): PageEdgeEntity = PageEdgeEntity(
    fromPageId = fromPageId,
    swipeDirection = swipeDirection,
    toPageId = toPageId,
)

fun List<PageEntity>.toPageInfos(): List<PageInfo> = map { it.toPageInfo() }

fun List<PageEdgeEntity>.toEdgeInfos(): List<EdgeInfo> = map { it.toEdgeInfo() }
