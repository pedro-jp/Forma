package com.forma.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Mapeia nomes de SF Symbols (usados no iOS) para o ícone Material mais próximo.
 * Nomes desconhecidos retornam um ícone neutro (círculo de informação).
 */
fun sfSymbol(name: String): ImageVector = when (name) {
    // Tabs
    "house.fill", "house" -> Icons.Filled.Home
    "fork.knife" -> Icons.Filled.Restaurant
    "dumbbell.fill", "dumbbell" -> Icons.Filled.FitnessCenter
    "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Filled.ShowChart
    "person.fill", "person", "person.crop.circle", "person.crop.circle.fill" -> Icons.Filled.Person
    // Ações
    "plus", "plus.circle.fill", "plus.circle" -> Icons.Filled.Add
    "minus", "minus.circle.fill" -> Icons.Filled.Remove
    "xmark" -> Icons.Filled.Close
    "xmark.circle.fill" -> Icons.Filled.Cancel
    "checkmark" -> Icons.Filled.Check
    "checkmark.circle.fill", "checkmark.circle", "checkmark.seal.fill" -> Icons.Filled.CheckCircle
    "trash", "trash.fill" -> Icons.Filled.Delete
    "pencil", "square.and.pencil" -> Icons.Filled.Edit
    "magnifyingglass" -> Icons.Filled.Search
    "arrow.clockwise", "arrow.triangle.2.circlepath" -> Icons.Filled.Refresh
    "square.and.arrow.up" -> Icons.Filled.Share
    "ellipsis", "ellipsis.circle" -> Icons.Filled.MoreHoriz
    "list.bullet" -> Icons.Filled.FormatListBulleted
    "line.3.horizontal" -> Icons.Filled.Menu
    "rectangle.portrait.and.arrow.right" -> Icons.AutoMirrored.Filled.Logout
    // Setas
    "chevron.right" -> Icons.Filled.ChevronRight
    "chevron.left" -> Icons.Filled.ChevronLeft
    "chevron.down" -> Icons.Filled.ExpandMore
    "chevron.up" -> Icons.Filled.ExpandLess
    "arrow.left" -> Icons.AutoMirrored.Filled.ArrowBack
    "arrow.right" -> Icons.AutoMirrored.Filled.ArrowForward
    "arrow.up" -> Icons.Filled.ArrowUpward
    "arrow.down" -> Icons.Filled.ArrowDownward
    "arrow.up.right" -> Icons.Filled.NorthEast
    "arrow.down.right" -> Icons.Filled.SouthEast
    "chart.line.uptrend" -> Icons.AutoMirrored.Filled.TrendingUp
    // Status
    "exclamationmark.triangle.fill", "exclamationmark.triangle", "exclamationmark.circle.fill" -> Icons.Filled.Warning
    "bolt.badge.clock.fill" -> Icons.Filled.Schedule
    "sparkles", "wand.and.stars" -> Icons.Filled.AutoAwesome
    "info.circle", "info.circle.fill" -> Icons.Filled.Info
    // Domínio
    "drop.fill", "drop" -> Icons.Filled.WaterDrop
    "flame.fill", "flame" -> Icons.Filled.LocalFireDepartment
    "bolt.fill", "bolt" -> Icons.Filled.Bolt
    "camera.fill", "camera" -> Icons.Filled.CameraAlt
    "photo", "photo.fill" -> Icons.Filled.Image
    "mic.fill", "mic" -> Icons.Filled.Mic
    "heart.fill", "heart" -> Icons.Filled.Favorite
    "figure.walk" -> Icons.Filled.DirectionsWalk
    "figure.run" -> Icons.Filled.DirectionsRun
    "scalemass.fill", "scalemass" -> Icons.Filled.MonitorWeight
    "timer" -> Icons.Filled.Timer
    "clock", "clock.fill" -> Icons.Filled.Schedule
    "calendar" -> Icons.Filled.CalendarMonth
    "star.fill" -> Icons.Filled.Star
    "star" -> Icons.Filled.StarBorder
    "gearshape.fill", "gearshape" -> Icons.Filled.Settings
    "bell.fill", "bell" -> Icons.Filled.Notifications
    "lock.fill", "lock" -> Icons.Filled.Lock
    "envelope.fill", "envelope" -> Icons.Filled.Email
    "eye", "eye.fill" -> Icons.Filled.Visibility
    "eye.slash", "eye.slash.fill" -> Icons.Filled.VisibilityOff
    "target", "scope" -> Icons.Filled.TrackChanges
    "trophy.fill", "trophy" -> Icons.Filled.EmojiEvents
    "play.fill", "play" -> Icons.Filled.PlayArrow
    "pause.fill", "pause" -> Icons.Filled.Pause
    "stop.fill", "stop" -> Icons.Filled.Stop
    "forward.fill", "forward.end.fill" -> Icons.Filled.SkipNext
    "doc.text", "doc.text.fill" -> Icons.Filled.Description
    "brain.head.profile", "brain" -> Icons.Filled.Psychology
    "leaf.fill", "leaf" -> Icons.Filled.Eco
    "bed.double.fill", "moon.fill", "moon.zzz.fill" -> Icons.Filled.Bedtime
    "location.fill", "mappin.and.ellipse" -> Icons.Filled.LocationOn
    else -> Icons.Filled.Info
}
