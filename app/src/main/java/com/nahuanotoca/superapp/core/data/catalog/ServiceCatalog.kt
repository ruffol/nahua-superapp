package com.nahuanotoca.superapp.core.data.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Shield
import com.nahuanotoca.superapp.core.design.Amber
import com.nahuanotoca.superapp.core.design.Coral
import com.nahuanotoca.superapp.core.design.Jade
import com.nahuanotoca.superapp.core.design.Rose
import com.nahuanotoca.superapp.core.design.Sky
import com.nahuanotoca.superapp.core.design.Teal
import com.nahuanotoca.superapp.core.design.Violet
import com.nahuanotoca.superapp.core.model.Service
import com.nahuanotoca.superapp.core.model.ServiceCategory
import com.nahuanotoca.superapp.core.model.ServiceTarget
import com.nahuanotoca.superapp.core.navigation.Routes

/**
 * Single source of truth for the service hub. Adding a service is a one-line change; mini-app
 * backed services point to a manifest under assets/miniapps.
 */
object ServiceCatalog {

    val all: List<Service> = listOf(
        Service(
            id = "ride",
            name = "Nahua Ride",
            tagline = "Muévete por la ciudad",
            category = ServiceCategory.TRANSPORT,
            icon = Icons.Rounded.DirectionsCar,
            accent = Coral,
            target = ServiceTarget.MiniApp("ride"),
            badge = "Nuevo",
        ),
        Service(
            id = "food",
            name = "Nahua Food",
            tagline = "Comida a domicilio",
            category = ServiceCategory.FOOD,
            icon = Icons.Rounded.Restaurant,
            accent = Amber,
            target = ServiceTarget.MiniApp("food"),
        ),
        Service(
            id = "bills",
            name = "Pagar servicios",
            tagline = "Luz, agua e internet",
            category = ServiceCategory.PAYMENTS,
            icon = Icons.Rounded.ReceiptLong,
            accent = Sky,
            target = ServiceTarget.MiniApp("bills"),
        ),
        Service(
            id = "cinema",
            name = "Cine",
            tagline = "Boletos en segundos",
            category = ServiceCategory.ENTERTAINMENT,
            icon = Icons.Rounded.Movie,
            accent = Violet,
            target = ServiceTarget.MiniApp("cinema"),
        ),
        Service(
            id = "wallet",
            name = "Cartera",
            tagline = "Saldo y movimientos",
            category = ServiceCategory.PAYMENTS,
            icon = Icons.Rounded.AccountBalanceWallet,
            accent = Jade,
            target = ServiceTarget.Native(Routes.WALLET),
        ),
        Service(
            id = "scan",
            name = "Escanear QR",
            tagline = "Paga al instante",
            category = ServiceCategory.PAYMENTS,
            icon = Icons.Rounded.QrCodeScanner,
            accent = Teal,
            target = ServiceTarget.Native(Routes.SCAN),
        ),
        Service(
            id = "inbox",
            name = "Avisos",
            tagline = "Todo lo que pasa",
            category = ServiceCategory.LIFESTYLE,
            icon = Icons.Rounded.Notifications,
            accent = Sky,
            target = ServiceTarget.Native(Routes.INBOX),
            badge = "4",
        ),
        Service(
            id = "profile",
            name = "Mi cuenta",
            tagline = "Datos y ajustes",
            category = ServiceCategory.LIFESTYLE,
            icon = Icons.Rounded.Person,
            accent = Jade,
            target = ServiceTarget.Native(Routes.PROFILE),
        ),
        Service(
            id = "topup",
            name = "Recarga celular",
            tagline = "Saldo en un toque",
            category = ServiceCategory.PAYMENTS,
            icon = Icons.Rounded.PhoneAndroid,
            accent = Rose,
            target = ServiceTarget.ComingSoon,
            badge = "Pronto",
        ),
        Service(
            id = "transit",
            name = "Transporte público",
            tagline = "Recarga tu tarjeta",
            category = ServiceCategory.TRANSPORT,
            icon = Icons.Rounded.DirectionsBus,
            accent = Sky,
            target = ServiceTarget.ComingSoon,
        ),
        Service(
            id = "pharmacy",
            name = "Farmacia",
            tagline = "Medicamentos 24/7",
            category = ServiceCategory.LIFESTYLE,
            icon = Icons.Rounded.LocalPharmacy,
            accent = Coral,
            target = ServiceTarget.ComingSoon,
        ),
        Service(
            id = "insurance",
            name = "Seguros",
            tagline = "Protege lo importante",
            category = ServiceCategory.LIFESTYLE,
            icon = Icons.Rounded.Shield,
            accent = Violet,
            target = ServiceTarget.ComingSoon,
        ),
        Service(
            id = "gov",
            name = "Trámites",
            tagline = "Gobierno en línea",
            category = ServiceCategory.GOVERNMENT,
            icon = Icons.Rounded.AccountBalance,
            accent = Jade,
            target = ServiceTarget.ComingSoon,
        ),
        Service(
            id = "predial",
            name = "Predial",
            tagline = "Paga tu propiedad",
            category = ServiceCategory.GOVERNMENT,
            icon = Icons.Rounded.Home,
            accent = Amber,
            target = ServiceTarget.ComingSoon,
        ),
    )

    val featured: List<Service> = listOf(
        "ride", "food", "bills", "cinema", "wallet", "scan", "inbox", "profile",
    ).mapNotNull { id -> all.firstOrNull { it.id == id } }

    fun byCategory(category: ServiceCategory): List<Service> = all.filter { it.category == category }

    fun find(id: String): Service? = all.firstOrNull { it.id == id }

    fun search(query: String): List<Service> {
        val term = query.trim()
        if (term.isEmpty()) return all
        return all.filter { service ->
            service.name.contains(term, ignoreCase = true) ||
                service.tagline.contains(term, ignoreCase = true) ||
                service.category.label.contains(term, ignoreCase = true)
        }
    }
}
