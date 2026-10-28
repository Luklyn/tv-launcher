package main

import "regexp"

type BloatItem struct {
	Pkg       string `json:"pkg"`
	Label     string `json:"label"`
	Desc      string `json:"desc"`
	Group     string `json:"group"`
	Default   bool   `json:"default"`
	Installed *bool  `json:"installed,omitempty"`
}

const (
	gTelemetry = "Publicité et télémétrie"
	gReco      = "Recommandations de l'écran d'accueil d'origine"
	gOptional  = "Optionnel / avancé"
)

// Liste de paquets courants sur Android TV / Google TV. Seuls ceux présents sur la TV sont proposés.
// Tout est désactivé avec « pm disable-user » : rien n'est supprimé et « Tout restaurer » remet tout.
var catalog = []BloatItem{
	{"com.google.android.feedback", "Commentaires Google", "Envoi de rapports d'erreur et de commentaires à Google", gTelemetry, true, nil},
	{"com.google.android.onetimeinitializer", "Initialisation Google", "Service d'initialisation à l'usage unique", gTelemetry, true, nil},
	{"com.google.android.partnersetup", "Configuration partenaires", "Préinstallations et réglages imposés par des partenaires", gTelemetry, true, nil},
	{"com.android.providers.partnerbookmarks", "Marque-pages partenaires", "Liens préchargés par le fabricant", gTelemetry, true, nil},
	{"com.google.android.adservices.api", "Services publicitaires Android", "Privacy Sandbox : profilage publicitaire", gTelemetry, true, nil},
	{"com.google.android.ext.adservices.api", "Services publicitaires (extension)", "Privacy Sandbox : profilage publicitaire", gTelemetry, true, nil},
	{"com.google.mainline.adservices", "Services publicitaires (module)", "Privacy Sandbox : profilage publicitaire", gTelemetry, true, nil},
	{"com.android.traceur", "Traçage système", "Collecte de traces de performance", gTelemetry, true, nil},
	{"com.netflix.partner.activation", "Activation partenaire Netflix", "Mécanisme de préinstallation Netflix", gTelemetry, true, nil},

	{"com.google.android.tvrecommendations", "Recommandations Android TV", "Alimente les rangées sponsorisées de l'accueil d'origine", gReco, true, nil},
	{"com.google.android.leanbacklauncher.recommendations", "Recommandations (ancien)", "Ancien service de recommandations", gReco, true, nil},

	{"com.google.android.videos", "Google TV (Films)", "Application de location et d'achat de films", gOptional, false, nil},
	{"com.google.android.music", "Google Play Musique", "Ancien lecteur musical", gOptional, false, nil},
	{"com.google.android.play.games", "Google Play Jeux", "Services de jeux", gOptional, false, nil},
	{"com.google.android.backdrop", "Économiseur d'écran Ambient", "Photos et informations en veille", gOptional, false, nil},
	{"com.google.android.tvlauncher", "Écran d'accueil d'origine (Android TV)", "À ne désactiver qu'une fois ton launcher installé ET testé", gOptional, false, nil},
	{"com.google.android.apps.tv.launcherx", "Écran d'accueil d'origine (Google TV)", "À ne désactiver qu'une fois ton launcher installé ET testé", gOptional, false, nil},
}

var detectRe = regexp.MustCompile(`(?i)(analytics|telemetry|crashlytics|adservice|\.ads?(\.|$)|advert|tracking|metrics|usagestats|bugreport|feedback)`)

// Paquets que la détection automatique ne doit jamais proposer.
var protectedRe = regexp.MustCompile(`^(android$|com\.android\.(systemui|settings|providers\.(media|tv|settings|contacts|downloads|calendar|telephony)|inputmethod|shell|packageinstaller|permissioncontroller|bluetooth|tv\.|server\.telecom)|com\.google\.android\.(gms|gsf|tv\.|tvlauncher|inputmethod\.|packageinstaller|permissioncontroller|webview|trichromelibrary)|org\.droidtv\.|com\.custom\.tvlauncher)`)
