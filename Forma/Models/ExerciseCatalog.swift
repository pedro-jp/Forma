//
//  ExerciseCatalog.swift
//  Forma
//
//  Created by João Pedro on 10/30/26.
//

import Foundation

/// Item individual do catálogo de exercícios com anatomia, vídeo 3D de 10s e guia biomecânico
public struct ExerciseCatalogItem: Identifiable, Sendable, Equatable {
    public let id: String
    public let name: String
    public let muscleGroup: String
    public let emphasisMuscle: String
    public let targetMuscles: String
    public let gifName: String
    public let setupInstruction: String
    public let eccentricInstruction: String
    public let concentricInstruction: String
    public let breathingInstruction: String
    public let commonMistake: String
    public let synonyms: [String]

    /// Nome canônico do arquivo de vídeo MP4 de 10s em `ExerciseVideos/<videoName>.mp4`
    public var videoName: String { gifName }

    /// ID do exercício principal ao qual este movimento pertence (igual ao próprio `id` se for principal)
    public var mainExerciseId: String {
        ExerciseCatalog.variationParentMap[id] ?? id
    }

    /// Indica se este movimento é uma variação de um dos 27 exercícios principais
    public var isVariation: Bool {
        mainExerciseId != id
    }
    
    public init(
        id: String,
        name: String,
        muscleGroup: String,
        emphasisMuscle: String,
        targetMuscles: String,
        gifName: String,
        setupInstruction: String,
        eccentricInstruction: String,
        concentricInstruction: String,
        breathingInstruction: String,
        commonMistake: String,
        synonyms: [String] = []
    ) {
        self.id = id
        self.name = name
        self.muscleGroup = muscleGroup
        self.emphasisMuscle = emphasisMuscle
        self.targetMuscles = targetMuscles
        self.gifName = gifName
        self.setupInstruction = setupInstruction
        self.eccentricInstruction = eccentricInstruction
        self.concentricInstruction = concentricInstruction
        self.breathingInstruction = breathingInstruction
        self.commonMistake = commonMistake
        self.synonyms = synonyms
    }
}

/// Catálogo central de exercícios, biomecânica e associação inteligente com IA
public final class ExerciseCatalog: @unchecked Sendable {
    public static let shared = ExerciseCatalog()
    
    public let items: [ExerciseCatalogItem]
    
    public var muscleGroups: [String] {
        return ["Peitoral", "Costas", "Pernas", "Ombros", "Bíceps", "Tríceps", "Abdômen"]
    }
    
    private init() {
        self.items = [
            ExerciseCatalogItem(
                id: "supino_reto",
                name: "Supino Reto com Barra",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior (Feixe Esternocostal)",
                targetMuscles: "Peitoral Maior, Deltoide Anterior, Tríceps Braquial",
                gifName: "supino_reto",
                setupInstruction: "Deite-se no banco plano com os pés bem firmes no chão. Mantenha os 5 pontos de contato: cabeça, escápulas, glúteos e os dois pés. Segure a barra com pegada ligeiramente mais larga que a largura dos ombros.",
                eccentricInstruction: "Inspire e desça a barra de forma controlada até tocar suavemente a linha média dos mamilos. Mantenha os cotovelos a aproximadamente 45° a 75° do tronco (evite cotovelos a 90° para proteger o manguito).",
                concentricInstruction: "Expire e empurre a barra em linha vertical/ligeiramente diagonal em direção aos olhos até a extensão dos braços, contraindo o peitoral no topo sem desencaixar as escápulas.",
                breathingInstruction: "Inspire na descida (fase excêntrica) e expire na subida ao vencer a carga (fase concêntrica).",
                commonMistake: "Rebater a barra no esterno ou abrir excessivamente os cotovelos a 90°.",
                synonyms: ["supino", "supino reto", "bench press", "supino barra", "supino reto barra"]
            ),
            ExerciseCatalogItem(
                id: "supino_inclinado",
                name: "Supino Inclinado com Barra",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Superior (Feixe Clavicular)",
                targetMuscles: "Peitoral Superior, Deltoide Anterior, Tríceps",
                gifName: "supino_inclinado",
                setupInstruction: "Ajuste o banco em inclinação de 30° a 45°. Pés firmes no solo, escápulas em depressão e retração mantidas contra o encosto durante todo o movimento.",
                eccentricInstruction: "Desça a barra controladamente em direção à parte superior do tórax (logo abaixo da clavícula), sentindo o feixe superior se alongar.",
                concentricInstruction: "Empurre a barra para cima e levemente para trás em direção à linha dos olhos, focando na contração da porção clavicular.",
                breathingInstruction: "Inspire na descida e expire no momento de maior esforço na subida.",
                commonMistake: "Inclinar o banco a mais de 45°, o que transfere o esforço quase todo para os deltoides anteriores.",
                synonyms: ["supino inclinado", "incline bench press", "supino inclinado barra"]
            ),
            ExerciseCatalogItem(
                id: "supino_declinado",
                name: "Supino Declinado com Barra",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Inferior (Feixe Abdominal)",
                targetMuscles: "Peitoral Inferior, Tríceps Braquial, Deltoide Anterior",
                gifName: "supino_declinado",
                setupInstruction: "Prenda os tornozelos nos suportes do banco declinado (-15° a -30°). Deite-se com as escápulas aduzidas e pegada firme na barra.",
                eccentricInstruction: "Desça a barra lentamente até a linha inferior do peitoral, controlando a velocidade para evitar impacto no tronco.",
                concentricInstruction: "Empurre a barra verticalmente até estender os braços, contraindo a porção inferior do peitoral com firmeza.",
                breathingInstruction: "Inspire na descida e expire ao empurrar a barra para cima.",
                commonMistake: "Permitir que a barra desça muito próxima ao pescoço.",
                synonyms: ["supino declinado", "decline bench press", "supino canadense"]
            ),
            ExerciseCatalogItem(
                id: "supino_inclinado_halteres",
                name: "Supino Inclinado com Halteres",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Superior (Feixe Clavicular)",
                targetMuscles: "Peitoral Superior, Deltoide Anterior, Tríceps",
                gifName: "supino_inclinado_halteres",
                setupInstruction: "Banco ajustado entre 30° e 45°. Apoie os halteres nas coxas e dê um impulso com os joelhos para posicioná-los ao lado dos ombros com os punhos neutros ou levemente pronados.",
                eccentricInstruction: "Desça os halteres abrindo o peito e permitindo uma amplitude profunda, mantendo os antebraços sempre perpendiculares ao chão.",
                concentricInstruction: "Empurre os halteres para cima e para o centro em trajetória de arco suave até que quase se toquem no topo, esmagando o miolo e a porção superior do peito.",
                breathingInstruction: "Inspire na descida sentindo o peitoral abrir e expire ao empurrar.",
                commonMistake: "Bater os halteres com força no topo ou deixar os antebraços inclinarem para dentro na descida.",
                synonyms: ["supino com alter inclinado", "supino com halter inclinado", "supino inclinado halteres", "supino inclinado halter", "incline dumbbell press", "supino halter inclinado"]
            ),
            ExerciseCatalogItem(
                id: "supino_reto_halteres",
                name: "Supino Reto com Halteres",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior",
                targetMuscles: "Peitoral Maior, Tríceps, Deltoide Anterior",
                gifName: "supino_reto_halteres",
                setupInstruction: "Deite-se no banco plano com um par de halteres na altura do peito. Mantenha os pés cravados no chão e as escápulas retraídas.",
                eccentricInstruction: "Desça os halteres em movimento controlado com amplitude maior que a barra permite, sentindo o alongamento total das fibras peitorais.",
                concentricInstruction: "Empurre os halteres para cima até a extensão completa, mantendo a contração e tensão constante no peitoral.",
                breathingInstruction: "Inspire na descida e expire na subida.",
                commonMistake: "Tirar as costas do banco ou deixar os halteres caírem descontroladamente.",
                synonyms: ["supino reto halter", "supino reto halteres", "dumbbell bench press", "supino halter"]
            ),
            ExerciseCatalogItem(
                id: "crucifixo",
                name: "Crucifixo Reto com Halteres",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior (Alongamento e Miolo)",
                targetMuscles: "Peitoral Maior, Deltoide Anterior",
                gifName: "crucifixo",
                setupInstruction: "Deitado no banco plano, segure os halteres acima do peito com pegada neutra (palmas voltadas uma para a outra). Cotovelos ligeiramente flexionados (fixos a 15°).",
                eccentricInstruction: "Abra os braços em arco suave até que os cotovelos fiquem na linha do tronco, sentindo o peitoral alongar por completo.",
                concentricInstruction: "Feche os braços imaginando que está abraçando um barril largo, até os halteres voltarem ao topo sem flexionar mais os cotovelos.",
                breathingInstruction: "Inspire profundamente na abertura e expire no fechamento.",
                commonMistake: "Transformar o crucifixo em um supino flexionando e estendendo os cotovelos durante a execução.",
                synonyms: ["crucifixo", "crucifixo reto", "chest fly", "crucifixo halteres"]
            ),
            ExerciseCatalogItem(
                id: "crucifixo_inclinado",
                name: "Crucifixo Inclinado com Halteres",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Superior (Porção Clavicular)",
                targetMuscles: "Peitoral Superior, Deltoide Anterior",
                gifName: "crucifixo_inclinado",
                setupInstruction: "Banco ajustado em 30° a 45°. Segure os halteres acima dos olhos com palmas apontadas uma para a outra e cotovelos com leve flexão anatômica.",
                eccentricInstruction: "Abra os braços controladamente em arco amplo, sentindo o alongamento isolado da parte superior do tórax.",
                concentricInstruction: "Traga os halteres de volta ao topo mantendo o ângulo fixo dos cotovelos, focando na contração no fechamento.",
                breathingInstruction: "Inspire na descida/abertura e expire no pico de contração no topo.",
                commonMistake: "Carga excessiva que impede a amplitude completa de abertura.",
                synonyms: ["crucifixo inclinado", "incline dumbbell fly", "fly inclinado", "crucifixo inclinado halteres"]
            ),
            ExerciseCatalogItem(
                id: "crossover_polia",
                name: "Crossover na Polia Média/Alta",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior (Isolamento e Tensão Contínua)",
                targetMuscles: "Peitoral Maior, Deltoide Anterior",
                gifName: "crossover_polia",
                setupInstruction: "Posicione as polias na altura média/alta. Dê um passo à frente com uma perna para estabilizar a base e incline levemente o tronco à frente.",
                eccentricInstruction: "Permita que os cabos abram seus braços com controle até sentir o alongamento máximo do tórax.",
                concentricInstruction: "Puxe os cabos para frente e para baixo unindo as mãos na linha da cintura ou umbigo, cruzando levemente as mãos para pico de contração.",
                breathingInstruction: "Inspire na abertura e expire no momento do cruzamento.",
                commonMistake: "Usar impulso do tronco balançando para frente e para trás.",
                synonyms: ["crossover", "cross over", "crossover polia", "cable fly", "crossover cabo"]
            ),
            ExerciseCatalogItem(
                id: "voador_peck_deck",
                name: "Voador / Peck Deck",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior e Miolo",
                targetMuscles: "Peitoral Maior, Esternal, Deltoide Anterior",
                gifName: "voador_peck_deck",
                setupInstruction: "Ajuste a altura do banco para que os braços fiquem na linha do peitoral médio. Escápulas firmes no encosto.",
                eccentricInstruction: "Abra os braços permitindo que o peso alongue as fibras peitorais sem deixar os ombros projetarem para a frente.",
                concentricInstruction: "Aperte as alavancas em direção ao centro com força, sustentando a contração por 1 segundo no ponto de encontro.",
                breathingInstruction: "Inspire na abertura e expire na aproximação das manoplas.",
                commonMistake: "Descolar as costas do encosto e forçar os ombros para frente.",
                synonyms: ["voador", "peck deck", "peck deck máquina", "fly máquina"]
            ),
            ExerciseCatalogItem(
                id: "flexao_de_braco",
                name: "Flexão de Braço no Solo",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Maior e Estabilidade do Core",
                targetMuscles: "Peitoral Maior, Tríceps, Deltoide Anterior, Core",
                gifName: "flexao_de_braco",
                setupInstruction: "Mãos no chão ligeiramente mais largas que a linha dos ombros. Corpo em linha reta da cabeça aos calcanhares com abdômen e glúteos contraídos.",
                eccentricInstruction: "Desça o peito em direção ao chão até quase encostar, mantendo os cotovelos em ângulo de 45° com o corpo.",
                concentricInstruction: "Empurre o chão com firmeza estendendo os braços e mantendo o alinhamento de prancha sem dobrar o quadril.",
                breathingInstruction: "Inspire na descida e expire ao empurrar o solo.",
                commonMistake: "Deixar o quadril ceder ou levantar o quadril em excesso.",
                synonyms: ["flexao", "flexão", "push up", "flexao de braco", "flexao solo"]
            ),
            ExerciseCatalogItem(
                id: "paralelas_peito",
                name: "Paralelas com Foco no Peitoral",
                muscleGroup: "Peitoral",
                emphasisMuscle: "Peitoral Inferior e Esternal",
                targetMuscles: "Peitoral Inferior, Tríceps, Deltoide Anterior",
                gifName: "paralelas_peito",
                setupInstruction: "Suspenda-se nas barras paralelas com os braços estendidos. Incline o tronco para a frente a cerca de 30° e cruze as pernas atrás.",
                eccentricInstruction: "Desça o corpo dobrando os cotovelos apontados para fora até que o peito fique paralelo às mãos, sentindo o peitoral esticar.",
                concentricInstruction: "Empurre com a força do tórax até voltar ao topo sem verticalizar o tronco para manter o peitoral sob foco.",
                breathingInstruction: "Inspire na descida e expire na subida.",
                commonMistake: "Ficar totalmente reto (o que transfere a ênfase para os tríceps).",
                synonyms: ["paralelas peito", "dips peito", "paralela peitoral", "mergulho paralelas peito"]
            ),
            ExerciseCatalogItem(
                id: "puxada_alta",
                name: "Puxada Alta no Pulley",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal (Largura das Costas)",
                targetMuscles: "Grande Dorsal, Redondo Maior, Bíceps, Romboides",
                gifName: "puxada_alta",
                setupInstruction: "Sente-se no aparelho de puxada com as coxas bem presas nos rolos de apoio. Segure a barra longa com pegada pronada aberta.",
                eccentricInstruction: "Retorne a barra controladamente permitindo que as escápulas se elevem e as dorsais se alonguem ao máximo no topo.",
                concentricInstruction: "Puxe a barra em direção à parte superior do peito, deprimindo as escápulas e levando os cotovelos para baixo e para trás.",
                breathingInstruction: "Expire na puxada e inspire na subida controlada.",
                commonMistake: "Puxar a barra por trás da nuca ou inclinar o tronco excessivamente para trás.",
                synonyms: ["puxada alta", "lat pulldown", "pulley frente", "puxada aberta"]
            ),
            ExerciseCatalogItem(
                id: "remada_curvada",
                name: "Remada Curvada com Barra",
                muscleGroup: "Costas",
                emphasisMuscle: "Dorsal e Espessura das Costas",
                targetMuscles: "Grande Dorsal, Romboides, Trapézio Médio/Inferior, Bíceps",
                gifName: "remada_curvada",
                setupInstruction: "Pés na largura dos ombros, joelhos semiflexionados. Incline o tronco para frente a cerca de 45° mantendo a curvatura lombar anatômica e pegada pronada.",
                eccentricInstruction: "Estenda os braços para baixo com controle, mantendo a postura firme sem arredondar as costas.",
                concentricInstruction: "Puxe a barra em direção ao umbigo/quadril, focando em puxar com os cotovelos e esmagar as escápulas no final.",
                breathingInstruction: "Expire na puxada e inspire na descida.",
                commonMistake: "Arredondar a coluna lombar ou usar impulso excessivo do quadril.",
                synonyms: ["remada curvada", "barbell row", "remada barra", "remada curvada barra"]
            ),
            ExerciseCatalogItem(
                id: "remada_baixa",
                name: "Remada Baixa na Polia com Triângulo",
                muscleGroup: "Costas",
                emphasisMuscle: "Romboides e Miolo das Costas",
                targetMuscles: "Grande Dorsal, Romboides, Trapézio Médio, Bíceps",
                gifName: "remada_baixa",
                setupInstruction: "Sente-se no aparelho com os pés apoiados e joelhos levemente flexionados. Segure o triângulo mantendo a coluna ereta e peito aberto.",
                eccentricInstruction: "Permita que o peso leve os braços à frente alongando as escápulas, mantendo a coluna estável.",
                concentricInstruction: "Puxe o triângulo contra a altura do abdômen inferior, projetando o peito para frente e aproximando as escápulas.",
                breathingInstruction: "Expire na tração e inspire no retorno.",
                commonMistake: "Balançar o tronco excessivamente como se estivesse remando num barco.",
                synonyms: ["remada baixa", "seated cable row", "remada triangulo", "remada sentada"]
            ),
            ExerciseCatalogItem(
                id: "remada_unilateral",
                name: "Remada Unilateral com Haltere (Serrote)",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal (Amplitude e Pico Unilateral)",
                targetMuscles: "Grande Dorsal, Romboides, Deltoide Posterior, Bíceps",
                gifName: "remada_unilateral",
                setupInstruction: "Apoie um joelho e a mão do mesmo lado em um banco plano. Tronco paralelo ao chão. Segure o haltere com o outro braço estendido para baixo.",
                eccentricInstruction: "Desça o haltere sentindo a dorsal alongar por completo, mantendo a coluna estável e neutra.",
                concentricInstruction: "Puxe o haltere levando o cotovelo rente ao corpo em direção ao quadril, contraindo a dorsal no ponto mais alto.",
                breathingInstruction: "Expire na subida do haltere e inspire na descida.",
                commonMistake: "Girar o tronco para facilitar a subida do peso.",
                synonyms: ["remada unilateral", "serrote", "one arm dumbbell row", "remada serrote"]
            ),
            ExerciseCatalogItem(
                id: "barra_fixa",
                name: "Barra Fixa Pronada (Pull-Up)",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal e Cintura Escapular",
                targetMuscles: "Grande Dorsal, Redondo Maior, Bíceps, Braquial, Core",
                gifName: "barra_fixa",
                setupInstruction: "Segure a barra com pegada pronada (palmas para frente) um pouco mais larga que os ombros, corpo suspenso.",
                eccentricInstruction: "Desça o corpo em ritmo controlado até a extensão completa dos braços e elevação natural das escápulas.",
                concentricInstruction: "Puxe o corpo para cima levando o queixo acima da barra, mantendo o peito apontado para ela.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Dar 'chutes' ou impulsos de perna (kipping).",
                synonyms: ["barra fixa", "pull up", "barra fixa pronada", "pullup"]
            ),
            ExerciseCatalogItem(
                id: "pulldown_corda",
                name: "Pulldown na Polia com Corda",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal (Isolamento em Extensão)",
                targetMuscles: "Grande Dorsal, Redondo Maior, Tríceps (Cabeça Longa)",
                gifName: "pulldown_corda",
                setupInstruction: "Fique em pé de frente para a polia alta com uma corda fixada. Incline o tronco a cerca de 30° e segure as pontas da corda.",
                eccentricInstruction: "Deixe os braços subirem com controle até a altura da cabeça, esticando as dorsais.",
                concentricInstruction: "Puxe a corda para baixo em arco com os braços quase retos até a linha dos quadris, abrindo as mãos no final.",
                breathingInstruction: "Expire ao puxar a corda e inspire ao retornar.",
                commonMistake: "Flexionar os cotovelos como se fosse um tríceps.",
                synonyms: ["pulldown", "pulldown corda", "straight arm pulldown", "pull down"]
            ),
            ExerciseCatalogItem(
                id: "levantamento_terra",
                name: "Levantamento Terra Convencional",
                muscleGroup: "Costas",
                emphasisMuscle: "Cadeia Posterior e Eretores da Espinha",
                targetMuscles: "Eretores da Espinha, Glúteos, Isquiotibiais, Trapézio, Dorsal",
                gifName: "levantamento_terra",
                setupInstruction: "Barra colada nas canelas. Pés na largura do quadril. Quadril flexionado para trás, peito erguido, coluna neutra e pegada firme na barra.",
                eccentricInstruction: "Empurre o quadril para trás mantendo a barra colada nas coxas e canelas até ela tocar suavemente o solo.",
                concentricInstruction: "Empurre o chão com os pés estendendo joelhos e quadris simultaneamente até ficar totalmente ereto, travando os glúteos no topo.",
                breathingInstruction: "Inspire e faça a manobra de valsalva antes de tirar a barra do chão; expire no topo.",
                commonMistake: "Arredondar a coluna lombar durante a puxada.",
                synonyms: ["levantamento terra", "deadlift", "terra"]
            ),
            ExerciseCatalogItem(
                id: "puxada_articulada",
                name: "Puxada Articulada na Máquina",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal e Redondo Maior",
                targetMuscles: "Grande Dorsal, Romboides, Bíceps",
                gifName: "puxada_articulada",
                setupInstruction: "Ajuste o assento para que o peito fique apoiado na almofada. Segure as manoplas articuladas.",
                eccentricInstruction: "Suba as manoplas com controle sentindo o alongamento da musculatura dorsal.",
                concentricInstruction: "Puxe as manoplas com força para baixo convergindo o movimento e apertando a parte inferior das costas.",
                breathingInstruction: "Expire ao puxar e inspire ao retornar ao topo.",
                commonMistake: "Soltar o peso de forma brusca sem controlar a descida.",
                synonyms: ["puxada articulada", "puxador articulado", "máquina puxada"]
            ),
            ExerciseCatalogItem(
                id: "remada_cavalinho",
                name: "Remada Cavalinho (Barra T)",
                muscleGroup: "Costas",
                emphasisMuscle: "Espessura Central das Costas",
                targetMuscles: "Grande Dorsal, Romboides, Trapézio Médio, Bíceps",
                gifName: "remada_cavalinho",
                setupInstruction: "Posicione-se sobre a barra T com pegada neutra ou pronada. Joelhos flexionados e tronco inclinado.",
                eccentricInstruction: "Desça o peso alongando os braços sem curvar a parte baixa das costas.",
                concentricInstruction: "Puxe a barra em direção ao abdômen, espremendo as escápulas no ápice da contração.",
                breathingInstruction: "Expire na puxada e inspire na descida controlada.",
                commonMistake: "Usar peso excessivo e perder o ângulo do tronco.",
                synonyms: ["remada cavalinho", "t bar row", "barra t", "cavalinho"]
            ),
            ExerciseCatalogItem(
                id: "pull_over",
                name: "Pull-Over com Haltere",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal e Serrátil Anterior",
                targetMuscles: "Grande Dorsal, Serrátil, Peitoral Maior",
                gifName: "pull_over",
                setupInstruction: "Deite-se transversalmente em um banco plano, apoiando apenas a parte superior das costas. Segure o haltere com as duas mãos em formato de cálice.",
                eccentricInstruction: "Desça o haltere para trás da cabeça em arco longo com os cotovelos semiflexionados até sentir a dorsal esticar.",
                concentricInstruction: "Traga o haltere de volta até a linha dos olhos usando a força das costas e peito.",
                breathingInstruction: "Inspire profundamente na descida e expire no retorno.",
                commonMistake: "Flexionar excessivamente os cotovelos virando um tríceps testa.",
                synonyms: ["pullover", "pull over", "pullover halter"]
            ),
            ExerciseCatalogItem(
                id: "chin_up",
                name: "Barra Fixa Supinada (Chin-Up)",
                muscleGroup: "Costas",
                emphasisMuscle: "Dorsal Inferior e Bíceps",
                targetMuscles: "Grande Dorsal, Bíceps Braquial, Braquiorradial",
                gifName: "chin_up",
                setupInstruction: "Segure a barra com pegada supinada (palmas voltadas para você) na largura dos ombros.",
                eccentricInstruction: "Desça controladamente até a extensão completa dos braços.",
                concentricInstruction: "Puxe o corpo para cima até passar o queixo da barra, sentindo o trabalho intenso de dorsais e bíceps.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Não descer até a extensão completa dos braços.",
                synonyms: ["chin up", "chinup", "barra supinada", "barra fixa fechada"]
            ),
            ExerciseCatalogItem(
                id: "agachamento_livre",
                name: "Agachamento Livre com Barra",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps e Glúteos",
                targetMuscles: "Quadríceps, Glúteo Máximo, Isquiotibiais, Core, Eretores",
                gifName: "agachamento_livre",
                setupInstruction: "Barra apoiada sobre o trapézio (costas superiores). Pés afastados na largura dos ombros, pontas apontando levemente para fora.",
                eccentricInstruction: "Inicie o movimento dobrando os quadris e joelhos simultaneamente. Desça até que a dobra do quadril fique abaixo do topo do joelho.",
                concentricInstruction: "Empurre os pés contra o chão subindo de volta à posição inicial com o peito erguido.",
                breathingInstruction: "Inspire antes de descer, trave o abdômen e expire ao passar pelo ponto de maior esforço.",
                commonMistake: "Joelhos colapsando para dentro (valgo dinâmico) ou levantar os calcanhares.",
                synonyms: ["agachamento", "agachamento livre", "squat", "barbell squat", "agachamento barra"]
            ),
            ExerciseCatalogItem(
                id: "leg_press",
                name: "Leg Press 45º",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps e Glúteos",
                targetMuscles: "Quadríceps, Glúteos, Isquiotibiais",
                gifName: "leg_press",
                setupInstruction: "Sente-se no aparelho mantendo a lombar e glúteos colados no encosto. Pés na largura dos ombros no meio da plataforma.",
                eccentricInstruction: "Destrave a carga e desça a plataforma flexionando os joelhos até pelo menos 90° sem arredondar a lombar.",
                concentricInstruction: "Empurre a plataforma de volta sem hiperestender ou travar bruscamente os joelhos no final.",
                breathingInstruction: "Inspire na flexão e expire na extensão das pernas.",
                commonMistake: "Tirar a lombar do encosto no final da descida ou estalar os joelhos no topo.",
                synonyms: ["leg press", "leg 45", "legpress"]
            ),
            ExerciseCatalogItem(
                id: "cadeira_extensora",
                name: "Cadeira Extensora",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps (Isolamento do Reto Femoral)",
                targetMuscles: "Reto Femoral, Vasto Lateral, Vasto Medial, Vasto Intermédio",
                gifName: "cadeira_extensora",
                setupInstruction: "Ajuste o encosto para que a dobra do joelho coincida exatamente com o eixo de rotação da máquina. Almofada logo acima dos pés.",
                eccentricInstruction: "Desça o peso lentamente controlando a fase de alongamento do quadríceps.",
                concentricInstruction: "Estenda as pernas até a linha horizontal, segurando o pico de contração por 1 segundo no topo.",
                breathingInstruction: "Expire na extensão e inspire na descida controlada.",
                commonMistake: "Usar peso excessivo e projetar o tronco para trás com impulso.",
                synonyms: ["cadeira extensora", "extensora", "leg extension"]
            ),
            ExerciseCatalogItem(
                id: "mesa_flexora",
                name: "Mesa Flexora Deitada",
                muscleGroup: "Pernas",
                emphasisMuscle: "Isquiotibiais (Posterior de Coxa)",
                targetMuscles: "Bíceps Femoral, Semitendíneo, Semimembranoso, Gastrocnêmio",
                gifName: "mesa_flexora",
                setupInstruction: "Deite-se de bruços na máquina com o rolo de apoio posicionado na altura dos tendões de Aquiles. Joelhos alinhados com o eixo.",
                eccentricInstruction: "Retorne o peso de maneira lenta até quase a extensão total das pernas sem deixar o peso bater.",
                concentricInstruction: "Flexione as pernas trazendo o rolo em direção aos glúteos, contraindo fortemente a parte de trás da coxa.",
                breathingInstruction: "Expire ao flexionar as pernas e inspire ao estender.",
                commonMistake: "Elevar o quadril da mesa durante a flexão.",
                synonyms: ["mesa flexora", "flexora deitada", "lying leg curl"]
            ),
            ExerciseCatalogItem(
                id: "cadeira_flexora",
                name: "Cadeira Flexora Sentada",
                muscleGroup: "Pernas",
                emphasisMuscle: "Isquiotibiais (Alongamento Inicial Maior)",
                targetMuscles: "Isquiotibiais, Gastrocnêmio",
                gifName: "cadeira_flexora",
                setupInstruction: "Sente-se com as costas bem apoiadas e a trava de coxas firmemente travada. Rolo logo abaixo das panturrilhas.",
                eccentricInstruction: "Estenda as pernas lentamente sentindo o posterior ser alongado sob carga constante.",
                concentricInstruction: "Puxe o rolo para baixo e para trás dos joelhos, segurando a contração máxima por 1 segundo.",
                breathingInstruction: "Expire na puxada e inspire na subida.",
                commonMistake: "Não ajustar a trava superior da coxa, permitindo folga no quadril.",
                synonyms: ["cadeira flexora", "flexora sentada", "seated leg curl"]
            ),
            ExerciseCatalogItem(
                id: "stiff",
                name: "Stiff com Barra / Halteres",
                muscleGroup: "Pernas",
                emphasisMuscle: "Isquiotibiais e Glúteos",
                targetMuscles: "Isquiotibiais, Glúteo Máximo, Eretores da Espinha",
                gifName: "stiff",
                setupInstruction: "Pés paralelos na largura do quadril. Joelhos com leve flexão fixa (15° a 20°). Segure a barra ou halteres à frente das coxas.",
                eccentricInstruction: "Empurre o quadril para trás mantendo as costas perfeitamente alinhadas, descendo o peso rente às pernas até sentir os posteriores esticarem.",
                concentricInstruction: "Contraia os glúteos e posteriores para puxar o tronco de volta à postura vertical.",
                breathingInstruction: "Inspire na descida e expire no final da subida.",
                commonMistake: "Arredondar as costas ou flexionar os joelhos durante a descida (transformando em agachamento).",
                synonyms: ["stiff", "stiff barra", "stiff halteres", "romanian deadlift", "rdl"]
            ),
            ExerciseCatalogItem(
                id: "panturrilha",
                name: "Panturrilha em Pé / Gêmeos",
                muscleGroup: "Pernas",
                emphasisMuscle: "Gastrocnêmio e Sóleo",
                targetMuscles: "Gastrocnêmio Medial/Lateral, Sóleo",
                gifName: "panturrilha",
                setupInstruction: "Apoie a ponta dos pés na borda do degrau da máquina. Joelhos retos (mas sem travar a articulação).",
                eccentricInstruction: "Desça os calcanhares o máximo possível sentindo o alongamento da fáscia e da panturrilha.",
                concentricInstruction: "Eleve os calcanhares empurrando com a ponta dos pés até a contração máxima, sustentando por 1 segundo no topo.",
                breathingInstruction: "Expire ao subir e inspire na descida.",
                commonMistake: "Fazer movimentos rápidos e curtos usando o reflexo elástico do tendão de Aquiles.",
                synonyms: ["panturrilha", "panturrilha em pe", "calf raise", "gêmeos", "gemeos"]
            ),
            ExerciseCatalogItem(
                id: "agachamento_bulgaro",
                name: "Agachamento Búlgaro",
                muscleGroup: "Pernas",
                emphasisMuscle: "Glúteo Máximo e Quadríceps Unilateral",
                targetMuscles: "Glúteo Máximo, Quadríceps, Isquiotibiais, Adutores",
                gifName: "agachamento_bulgaro",
                setupInstruction: "Apoie o peito de um dos pés atrás em um banco. Dê um passo largo à frente com a outra perna. Segure um haltere em cada mão.",
                eccentricInstruction: "Desça o quadril flexionando o joelho dianteiro até a coxa ficar paralela ao chão, mantendo o tronco levemente inclinado para frente.",
                concentricInstruction: "Empurre através do calcanhar dianteiro subindo de forma potente.",
                breathingInstruction: "Inspire na descida e expire ao subir.",
                commonMistake: "Colocar o pé da frente muito perto do banco sobrecarregando o joelho.",
                synonyms: ["agachamento bulgaro", "bulgarian split squat", "bulgaro", "afundo bulgaro"]
            ),
            ExerciseCatalogItem(
                id: "afundo",
                name: "Afundo / Passada com Halteres",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps e Glúteos",
                targetMuscles: "Quadríceps, Glúteo Máximo, Core",
                gifName: "afundo",
                setupInstruction: "Em pé com halteres nas mãos. Dê um passo largo para frente.",
                eccentricInstruction: "Desça até ambos os joelhos formarem ângulos de aproximadamente 90°, sem bater o joelho de trás no chão.",
                concentricInstruction: "Empurre com a perna dianteira retornando à posição inicial ou avançando na passada.",
                breathingInstruction: "Inspire na descida e expire na subida.",
                commonMistake: "Projetar o joelho dianteiro excessivamente além da ponta do pé.",
                synonyms: ["afundo", "passada", "lunge", "walking lunge", "passada com halteres"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_pelvica",
                name: "Elevação Pélvica (Hip Thrust)",
                muscleGroup: "Pernas",
                emphasisMuscle: "Glúteo Máximo (Pico de Contração)",
                targetMuscles: "Glúteo Máximo, Isquiotibiais, Core",
                gifName: "elevacao_pelvica",
                setupInstruction: "Apoie a parte superior das costas em um banco plano. Barra acolchoada sobre o quadril. Pés no chão alinhados com os joelhos.",
                eccentricInstruction: "Desça o quadril de forma controlada sem arredondar a lombar.",
                concentricInstruction: "Empurre o quadril para o teto através dos calcanhares, travando o glúteo no topo e alinhando joelhos, quadris e ombros.",
                breathingInstruction: "Expire ao elevar a pelve e inspire na descida.",
                commonMistake: "Hiperestender a coluna lombar no topo em vez de contrair os glúteos.",
                synonyms: ["elevacao pelvica", "hip thrust", "elevação de quadril", "gluteo barra"]
            ),
            ExerciseCatalogItem(
                id: "agachamento_hack",
                name: "Agachamento Hack Máquina",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps com Apoio de Coluna",
                targetMuscles: "Quadríceps, Glúteos",
                gifName: "agachamento_hack",
                setupInstruction: "Posicione os ombros nas almofadas do carrinho do Hack e apoie as costas no encosto. Pés na largura dos ombros.",
                eccentricInstruction: "Destrave e desça de forma contínua até flexionar os joelhos em 90° ou mais.",
                concentricInstruction: "Empurre a plataforma com os pés subindo com o quadríceps sob alta tensão.",
                breathingInstruction: "Inspire na descida e expire na subida.",
                commonMistake: "Descolar os calcanhares da plataforma durante o agachamento.",
                synonyms: ["hack squat", "agachamento hack", "hack machine"]
            ),
            ExerciseCatalogItem(
                id: "desenvolvimento",
                name: "Desenvolvimento com Halteres",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Anterior e Médio",
                targetMuscles: "Deltoide Anterior, Deltoide Lateral, Tríceps, Trapézio",
                gifName: "desenvolvimento",
                setupInstruction: "Sente-se em um banco com encosto em 80° a 85°. Segure os halteres na altura das orelhas com cotovelos abertos e punhos neutros/pronados.",
                eccentricInstruction: "Desça os halteres com controle até a linha das orelhas, mantendo o peito erguido.",
                concentricInstruction: "Empurre os halteres para cima em arco suave até a extensão dos braços sobre a cabeça sem bater os pesos.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Arquear excessivamente a lombar para empurrar a carga.",
                synonyms: ["desenvolvimento", "desenvolvimento halteres", "shoulder press", "overhead press halteres"]
            ),
            ExerciseCatalogItem(
                id: "desenvolvimento_barra",
                name: "Desenvolvimento Militar com Barra",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Anterior e Estabilidade",
                targetMuscles: "Deltoide Anterior, Lateral, Tríceps, Core",
                gifName: "desenvolvimento_barra",
                setupInstruction: "Em pé ou sentado. Segure a barra na altura das clavículas com pegada ligeiramente mais larga que os ombros.",
                eccentricInstruction: "Desça a barra controladamente rente ao rosto até apoiar nas clavículas.",
                concentricInstruction: "Empurre a barra verticalmente para o alto, passando a cabeça para frente assim que a barra passar do topo da cabeça.",
                breathingInstruction: "Expire ao empurrar a barra e inspire na descida.",
                commonMistake: "Inclinar o tronco excessivamente para trás usando o peitoral superior.",
                synonyms: ["desenvolvimento barra", "militar", "desenvolvimento militar", "ohp", "overhead press"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_lateral",
                name: "Elevação Lateral com Halteres",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Lateral (Largura dos Ombros)",
                targetMuscles: "Deltoide Lateral, Trapézio Superior",
                gifName: "elevacao_lateral",
                setupInstruction: "Em pé com halteres ao lado do corpo. Joelhos relaxados e tronco com inclinação de 5° para a frente. Cotovelos levemente flexionados.",
                eccentricInstruction: "Desça os halteres lentamente resistindo à gravidade até que quase toquem as coxas.",
                concentricInstruction: "Eleve os braços lateralmente até a altura dos ombros, liderando o movimento com os cotovelos como se estivesse derramando água de uma jarra.",
                breathingInstruction: "Expire na elevação e inspire na descida cadenciada.",
                commonMistake: "Dar impulso com as pernas ou encolher os ombros ativando o trapézio em vez do deltoide.",
                synonyms: ["elevacao lateral", "elevação lateral", "lateral raise", "elevacao lateral halteres"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_frontal",
                name: "Elevação Frontal com Halteres",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Anterior",
                targetMuscles: "Deltoide Anterior, Peitoral Superior",
                gifName: "elevacao_frontal",
                setupInstruction: "Em pé, segure os halteres repousados sobre a frente das coxas com pegada pronada ou neutra.",
                eccentricInstruction: "Desça os pesos em ritmo lento sem soltar a tensão.",
                concentricInstruction: "Eleve os braços à frente até a altura dos olhos, mantendo os cotovelos com leve flexão fixa.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Balançar o tronco para trás para impulsionar a carga.",
                synonyms: ["elevacao frontal", "elevação frontal", "front raise"]
            ),
            ExerciseCatalogItem(
                id: "crucifixo_invertido",
                name: "Crucifixo Invertido com Halteres",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Posterior",
                targetMuscles: "Deltoide Posterior, Romboides, Trapézio",
                gifName: "crucifixo_invertido",
                setupInstruction: "Incline o tronco para frente a quase 90° (ou deite-se de bruços em banco inclinado). Halteres apontados para o solo.",
                eccentricInstruction: "Feche os braços controlando o peso sem relaxar as costas.",
                concentricInstruction: "Abra os braços lateralmente puxando com os cotovelos e contraindo a parte de trás dos ombros.",
                breathingInstruction: "Expire ao abrir e inspire ao fechar.",
                commonMistake: "Usar peso pesado demais e dobrar os cotovelos como em uma remada.",
                synonyms: ["crucifixo invertido", "rear delt fly", "posterior de ombro"]
            ),
            ExerciseCatalogItem(
                id: "face_pull",
                name: "Face Pull na Polia com Corda",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Posterior e Manguito Rotador",
                targetMuscles: "Deltoide Posterior, Trapézio, Romboides, Infraespinhal",
                gifName: "face_pull",
                setupInstruction: "Ajuste a polia na altura dos olhos com a corda. Segure as pontas com os polegares voltados para você.",
                eccentricInstruction: "Deixe os braços retornarem à frente de forma lenta mantendo a postura firme.",
                concentricInstruction: "Puxe a corda em direção ao rosto separando as mãos e rodando os ombros para trás com os cotovelos altos.",
                breathingInstruction: "Expire na puxada e inspire no retorno.",
                commonMistake: "Abaixar os cotovelos transformando o exercício em uma remada.",
                synonyms: ["face pull", "facepull", "puxada facial"]
            ),
            ExerciseCatalogItem(
                id: "encolhimento",
                name: "Encolhimento com Halteres / Barra",
                muscleGroup: "Ombros",
                emphasisMuscle: "Trapézio Superior",
                targetMuscles: "Trapézio Superior e Elevador da Escápula",
                gifName: "encolhimento",
                setupInstruction: "Em pé com a barra ou halteres ao lado das coxas, coluna alinhada e olhar à frente.",
                eccentricInstruction: "Desça os ombros com controle alongando o pescoço e trapézios.",
                concentricInstruction: "Eleve os ombros diretamente para cima em direção às orelhas, segurando a contração máxima por 1 a 2 segundos.",
                breathingInstruction: "Expire na elevação e inspire na descida.",
                commonMistake: "Rolar os ombros em círculos (causa atrito e desgaste desnecessário na articulação).",
                synonyms: ["encolhimento", "shrug", "trapezio", "encolhimento halteres"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_lateral_polia",
                name: "Elevação Lateral na Polia Baixa",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Lateral sob Tensão Contínua",
                targetMuscles: "Deltoide Lateral",
                gifName: "elevacao_lateral_polia",
                setupInstruction: "Ajuste o cabo na posição mais baixa. Fique de lado e segure a manopla com o braço cruzado pela frente do corpo.",
                eccentricInstruction: "Desça o cabo resistindo à puxada da polia até cruzar à frente do quadril.",
                concentricInstruction: "Puxe a manopla lateralmente até a altura do ombro, sentindo a tensão contínua em todo o trajeto.",
                breathingInstruction: "Expire na subida e inspire na descida controlada.",
                commonMistake: "Usar o corpo para balançar a carga na saída do movimento.",
                synonyms: ["elevacao lateral polia", "lateral na polia", "cable lateral raise"]
            ),
            ExerciseCatalogItem(
                id: "desenvolvimento_arnold",
                name: "Desenvolvimento Arnold",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Completo com Rotação",
                targetMuscles: "Deltoide Anterior, Médio e Posterior, Tríceps",
                gifName: "desenvolvimento_arnold",
                setupInstruction: "Sentado com os halteres na frente do queixo, palmas voltadas para o corpo.",
                eccentricInstruction: "Desça os halteres invertendo a rotação até voltarem à posição supinada diante do queixo.",
                concentricInstruction: "Empurre os halteres para o alto rotacionando os punhos para fora até que as palmas fiquem para frente no topo.",
                breathingInstruction: "Expire ao subir e girar; inspire na descida.",
                commonMistake: "Executar o giro de forma descoordenada sem manter o controle da carga.",
                synonyms: ["arnold press", "desenvolvimento arnold", "desenvolvimento com giro"]
            ),
            ExerciseCatalogItem(
                id: "remada_alta",
                name: "Remada Alta na Polia / Barra",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Lateral e Trapézio",
                targetMuscles: "Deltoide Lateral, Trapézio, Bíceps",
                gifName: "remada_alta",
                setupInstruction: "Em pé com pegada na largura dos ombros na barra ou no cabo.",
                eccentricInstruction: "Desça a barra devagar até estender os braços.",
                concentricInstruction: "Puxe a barra para cima rente ao tronco, elevando os cotovelos acima da linha dos punhos até a altura do peito.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Pegada excessivamente fechada que comprime a articulação do ombro.",
                synonyms: ["remada alta", "upright row", "remada alta polia"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_lateral_inclinado",
                name: "Elevação Lateral no Banco Inclinado",
                muscleGroup: "Ombros",
                emphasisMuscle: "Pico de Tensão do Deltoide Lateral",
                targetMuscles: "Deltoide Lateral",
                gifName: "elevacao_lateral_inclinado",
                setupInstruction: "Deite-se de lado em um banco inclinado a cerca de 60°, segurando um haltere com o braço superior.",
                eccentricInstruction: "Desça o haltere até a lateral da coxa com controle absoluto.",
                concentricInstruction: "Eleve o haltere lateralmente até a altura do ombro, isolando o feixe lateral sem auxílio do trapézio.",
                breathingInstruction: "Expire ao subir e inspire ao descer.",
                commonMistake: "Girar o punho durante o movimento.",
                synonyms: ["elevacao lateral inclinado", "lateral no banco inclinado"]
            ),
            ExerciseCatalogItem(
                id: "rosca_direta",
                name: "Rosca Direta com Barra W / Reta",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Bíceps Braquial (Cabeça Curta e Longa)",
                targetMuscles: "Bíceps Braquial, Braquial, Braquiorradial",
                gifName: "rosca_direta",
                setupInstruction: "Em pé com pés afastados na largura dos quadris. Segure a barra com pegada supinada na largura dos ombros. Cotovelos colados ao lado do tronco.",
                eccentricInstruction: "Desça a barra de forma lenta e controlada até estender os braços quase por completo, sem balançar o corpo.",
                concentricInstruction: "Flexione os cotovelos levantando a barra em direção aos ombros, contraindo o bíceps com firmeza sem jogar os cotovelos para frente.",
                breathingInstruction: "Expire na subida e inspire na descida cadenciada.",
                commonMistake: "Usar impulso com a coluna lombar jogando o tronco para trás.",
                synonyms: ["rosca direta", "barbell curl", "rosca barra", "rosca direta barra"]
            ),
            ExerciseCatalogItem(
                id: "rosca_alternada",
                name: "Rosca Alternada com Halteres",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Bíceps Braquial e Supinador",
                targetMuscles: "Bíceps Braquial, Braquial, Braquiorradial",
                gifName: "rosca_alternada",
                setupInstruction: "Em pé ou sentado, segure os halteres ao lado do corpo com pegada neutra.",
                eccentricInstruction: "Desça o braço controladamente desfazendo a rotação até voltar à posição neutra ao lado da perna.",
                concentricInstruction: "Flexione um braço por vez enquanto roda o punho para fora (supinação) até a palma apontar para o ombro no topo.",
                breathingInstruction: "Expire ao subir e inspire ao retornar.",
                commonMistake: "Começar a rodar o punho tarde demais ou balançar os ombros.",
                synonyms: ["rosca alternada", "dumbbell alternate curl", "rosca com halteres"]
            ),
            ExerciseCatalogItem(
                id: "rosca_martelo",
                name: "Rosca Martelo com Halteres",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Braquiorradial e Braquial (Espessura do Braço)",
                targetMuscles: "Braquiorradial, Braquial, Bíceps Braquial",
                gifName: "rosca_martelo",
                setupInstruction: "Em pé ou sentado, segure os halteres com as palmas voltadas uma para a outra (pegada neutra permanente).",
                eccentricInstruction: "Desça os halteres mantendo a pegada neutra com velocidade controlada.",
                concentricInstruction: "Suba os halteres em direção aos ombros sem girar os punhos, sentindo o trabalho na lateral do antebraço e braço.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Girar os punhos durante o trajeto (deve permanecer neutro).",
                synonyms: ["rosca martelo", "hammer curl", "martelo"]
            ),
            ExerciseCatalogItem(
                id: "rosca_scott",
                name: "Rosca Scott com Barra W",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Bíceps Braquial (Isolamento da Cabeça Curta)",
                targetMuscles: "Bíceps Braquial, Braquial",
                gifName: "rosca_scott",
                setupInstruction: "Ajuste a altura do banco Scott para que a parte superior da almofada encaixe nas axilas. Braços totalmente apoiados no acolchoado.",
                eccentricInstruction: "Desça a barra lentamente até quase a extensão completa, sem hiperestender ou deixar a barra cair.",
                concentricInstruction: "Puxe a barra para cima flexionando os cotovelos sem descolar os braços da almofada.",
                breathingInstruction: "Expire na flexão e inspire no alongamento.",
                commonMistake: "Descolar o corpo do banco para puxar o peso.",
                synonyms: ["rosca scott", "preacher curl", "banco scott"]
            ),
            ExerciseCatalogItem(
                id: "rosca_concentrada",
                name: "Rosca Concentrada com Haltere",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Pico do Bíceps (Cabeça Longa)",
                targetMuscles: "Bíceps Braquial, Braquial",
                gifName: "rosca_concentrada",
                setupInstruction: "Sentado na ponta do banco com as pernas abertas. Apoie a parte de trás do tríceps na parte interna da coxa.",
                eccentricInstruction: "Desça o haltere devagar até a extensão quase completa do braço.",
                concentricInstruction: "Flexione o cotovelo trazendo o peso em direção ao peito, apertando o pico do bíceps no topo.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Movimentar a perna para impulsionar o cotovelo.",
                synonyms: ["rosca concentrada", "concentration curl", "concentrada"]
            ),
            ExerciseCatalogItem(
                id: "rosca_inclinada",
                name: "Rosca no Banco Inclinado 45º",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Cabeça Longa do Bíceps (Alongamento)",
                targetMuscles: "Bíceps Braquial (Cabeça Longa), Braquial",
                gifName: "rosca_inclinada",
                setupInstruction: "Deite-se no banco inclinado a 45° mantendo as costas bem coladas. Deixe os braços caírem verticalmente para trás.",
                eccentricInstruction: "Desça os halteres com extrema suavidade aproveitando o alongamento profundo do bíceps.",
                concentricInstruction: "Flexione os braços mantendo os cotovelos apontados para o chão, sem jogá-los para a frente.",
                breathingInstruction: "Expire ao subir e inspire na descida lenta.",
                commonMistake: "Tirar a cabeça ou as costas do banco inclinado.",
                synonyms: ["rosca inclinada", "incline dumbbell curl", "rosca 45"]
            ),
            ExerciseCatalogItem(
                id: "rosca_inversa",
                name: "Rosca Inversa com Barra",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Braquiorradial e Extensores do Punho",
                targetMuscles: "Braquiorradial, Braquial, Extensores do Antebraço",
                gifName: "rosca_inversa",
                setupInstruction: "Em pé com pegada pronada (palmas para baixo) na barra, na largura dos ombros.",
                eccentricInstruction: "Desça a barra controladamente mantendo os punhos retos e firmes.",
                concentricInstruction: "Flexione os cotovelos levantando a barra sem permitir que os punhos dobrem para cima.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Carga alta que faz o punho quebrar ou cotovelos abrirem.",
                synonyms: ["rosca inversa", "reverse curl", "rosca invertida"]
            ),
            ExerciseCatalogItem(
                id: "rosca_spider",
                name: "Rosca Spider no Banco Inclinado",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Pico da Cabeça Curta do Bíceps",
                targetMuscles: "Bíceps Braquial, Braquial",
                gifName: "rosca_spider",
                setupInstruction: "Deite-se de peito no encosto de um banco inclinado a 45°. Braços pendendo retos para o solo.",
                eccentricInstruction: "Desça a barra ou halteres verticalmente sob controle absoluto.",
                concentricInstruction: "Puxe o peso em direção à testa sem movimentar a parte superior dos braços.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Usar balanço do tronco para iniciar o movimento.",
                synonyms: ["rosca spider", "spider curl"]
            ),
            ExerciseCatalogItem(
                id: "rosca_martelo_corda",
                name: "Rosca Martelo na Polia com Corda",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Braquial e Antebraço com Tensão Contínua",
                targetMuscles: "Braquiorradial, Braquial, Bíceps",
                gifName: "rosca_martelo_corda",
                setupInstruction: "Fique em pé de frente para a polia baixa segurando a corda com pegada neutra.",
                eccentricInstruction: "Retorne a corda de forma suave sentindo a resistência do cabo.",
                concentricInstruction: "Puxe a corda para cima abrindo levemente as pontas no topo do movimento.",
                breathingInstruction: "Expire na flexão e inspire no retorno.",
                commonMistake: "Deixar os cotovelos irem para trás do corpo.",
                synonyms: ["martelo na polia", "rosca martelo cabo", "cable hammer curl"]
            ),
            ExerciseCatalogItem(
                id: "rosca_21",
                name: "Rosca 21 com Barra",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Resistência e Congestão Sanguínea (Pump)",
                targetMuscles: "Bíceps Braquial, Braquial",
                gifName: "rosca_21",
                setupInstruction: "Em pé com pegada supinada na barra W ou reta.",
                eccentricInstruction: "Mantenha o ritmo cadenciado em todas as 21 repetições.",
                concentricInstruction: "Realize 7 repetições da parte inferior até a metade (90°), depois 7 repetições da metade até o topo, e finalmente 7 repetições completas.",
                breathingInstruction: "Mantenha a respiração contínua e rítmica.",
                commonMistake: "Usar carga excessiva que impede a conclusão das 21 repetições.",
                synonyms: ["rosca 21", "21s", "biceps 21"]
            ),
            ExerciseCatalogItem(
                id: "rosca_bayesian",
                name: "Rosca Bayesian na Polia",
                muscleGroup: "Bíceps",
                emphasisMuscle: "Alongamento Máximo da Cabeça Longa",
                targetMuscles: "Bíceps Braquial",
                gifName: "rosca_bayesian",
                setupInstruction: "De costas para a polia baixa, dê um passo à frente segurando o estribo atrás do corpo com o braço estendido.",
                eccentricInstruction: "Permita que o cabo puxe o braço para trás suavemente, maximizando o alongamento do bíceps.",
                concentricInstruction: "Puxe a manopla para frente flexionando o cotovelo sem projetá-lo para frente.",
                breathingInstruction: "Expire na flexão e inspire no alongamento.",
                commonMistake: "Inclinar o tronco para compensar o peso.",
                synonyms: ["rosca bayesian", "bayesian curl", "rosca no cabo de costas"]
            ),
            ExerciseCatalogItem(
                id: "triceps_corda",
                name: "Tríceps na Polia com Corda",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Cabeça Lateral do Tríceps",
                targetMuscles: "Tríceps Braquial (Cabeça Lateral e Medial)",
                gifName: "triceps_corda",
                setupInstruction: "Prenda a corda na polia alta. Segure pelas pontas, incline levemente o tronco e mantenha os cotovelos travados na lateral do tórax.",
                eccentricInstruction: "Deixe a corda subir de forma controlada até os antebraços formarem um ângulo de cerca de 90° com os braços.",
                concentricInstruction: "Empurre a corda para baixo estendendo os braços por completo e abrindo as pontas da corda no final para pico de contração.",
                breathingInstruction: "Expire na extensão e inspire na subida.",
                commonMistake: "Deixar os cotovelos subirem junto com a corda ou afastar os cotovelos do tronco.",
                synonyms: ["triceps corda", "tríceps corda", "rope pushdown", "triceps polia corda"]
            ),
            ExerciseCatalogItem(
                id: "triceps_barra_reta",
                name: "Tríceps Pulley com Barra Reta",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Tríceps Braquial Geral (Cabeça Medial e Lateral)",
                targetMuscles: "Tríceps Braquial",
                gifName: "triceps_barra_reta",
                setupInstruction: "Prenda a barra reta na polia alta. Segure com pegada pronada na largura dos ombros com os cotovelos colados ao lado do tronco.",
                eccentricInstruction: "Suba a barra controladamente até o ângulo reto dos cotovelos.",
                concentricInstruction: "Empurre a barra verticalmente até travar os cotovelos, concentrando a força na parte de trás dos braços.",
                breathingInstruction: "Expire na descida da barra e inspire na subida.",
                commonMistake: "Jogar o peso do corpo sobre a barra usando o peito para empurrar.",
                synonyms: ["triceps pulley", "tríceps pulley", "triceps barra", "bar pushdown"]
            ),
            ExerciseCatalogItem(
                id: "triceps_testa",
                name: "Tríceps Testa com Barra W / Halteres",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Cabeça Longa e Medial do Tríceps",
                targetMuscles: "Tríceps Braquial",
                gifName: "triceps_testa",
                setupInstruction: "Deite-se no banco plano. Segure a barra W ou halteres com os braços estendidos ligeiramente inclinados para trás em relação à vertical.",
                eccentricInstruction: "Flexione apenas os cotovelos descendo a barra suavemente em direção à testa ou ligeiramente atrás da cabeça.",
                concentricInstruction: "Estenda os cotovelos de volta à posição inicial sem abrir os cotovelos para os lados.",
                breathingInstruction: "Inspire na descida e expire ao empurrar o peso de volta.",
                commonMistake: "Abrir os cotovelos para fora ou mover os ombros durante o exercício.",
                synonyms: ["triceps testa", "tríceps testa", "skull crusher", "testa"]
            ),
            ExerciseCatalogItem(
                id: "triceps_frances",
                name: "Tríceps Francês com Haltere",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Cabeça Longa do Tríceps (Alongamento Vertical)",
                targetMuscles: "Tríceps Braquial (Cabeça Longa)",
                gifName: "triceps_frances",
                setupInstruction: "Sentado ou em pé. Segure o haltere com as duas mãos em formato de concha acima da cabeça com os braços estendidos.",
                eccentricInstruction: "Desça o haltere por trás da nuca flexionando os cotovelos ao máximo para esticar a cabeça longa.",
                concentricInstruction: "Estenda os braços para cima até o topo, mantendo os cotovelos o mais fechados possível.",
                breathingInstruction: "Inspire na descida atrás da cabeça e expire na extensão total.",
                commonMistake: "Deixar os cotovelos abrirem excessivamente para os lados.",
                synonyms: ["triceps frances", "tríceps francês", "overhead dumbbell extension", "frances"]
            ),
            ExerciseCatalogItem(
                id: "triceps_coice",
                name: "Tríceps Coice com Haltere",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Pico de Contração do Tríceps",
                targetMuscles: "Tríceps Braquial (Cabeça Lateral)",
                gifName: "triceps_coice",
                setupInstruction: "Tronco inclinado para frente paralelo ao solo. Braço elevado com o cotovelo colado ao tronco a 90°.",
                eccentricInstruction: "Retorne o haltere suavemente à posição de 90° sem deixar o cotovelo cair.",
                concentricInstruction: "Estenda o antebraço totalmente para trás alinhando com o tronco, segurando a contração máxima por 1 segundo.",
                breathingInstruction: "Expire na extensão e inspire na flexão.",
                commonMistake: "Balançar o braço inteiro como um pêndulo.",
                synonyms: ["triceps coice", "tríceps coice", "kickback", "triceps kickback"]
            ),
            ExerciseCatalogItem(
                id: "mergulho_banco",
                name: "Mergulho no Banco",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Tríceps com Carga Corporal",
                targetMuscles: "Tríceps Braquial, Deltoide Anterior, Peitoral",
                gifName: "mergulho_banco",
                setupInstruction: "Apoie as mãos na borda de um banco atrás de você, dedos apontados para frente. Pernas estendidas à frente.",
                eccentricInstruction: "Desça o quadril próximo ao banco flexionando os cotovelos até 90°.",
                concentricInstruction: "Empurre com a palma das mãos estendendo os braços de volta ao topo.",
                breathingInstruction: "Inspire na descida e expire ao subir.",
                commonMistake: "Afastar o corpo do banco sobrecarregando a parte anterior dos ombros.",
                synonyms: ["mergulho banco", "bench dip", "mergulho", "triceps banco"]
            ),
            ExerciseCatalogItem(
                id: "supino_fechado",
                name: "Supino Fechado com Barra",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Tríceps com Alta Sobrecarga",
                targetMuscles: "Tríceps Braquial, Peitoral Maior, Deltoide Anterior",
                gifName: "supino_fechado",
                setupInstruction: "Deite no banco de supino. Segure a barra com pegada na largura dos ombros (cerca de 30 a 40 cm de distância entre as mãos).",
                eccentricInstruction: "Desça a barra mantendo os cotovelos bem rentes ao tronco até encostar na linha média do peito.",
                concentricInstruction: "Empurre a barra estendendo os cotovelos com foco na contração potente dos tríceps.",
                breathingInstruction: "Inspire na descida e expire na subida.",
                commonMistake: "Pegada excessivamente junta (menos de 20 cm), o que machuca os punhos.",
                synonyms: ["supino fechado", "close grip bench press", "supino triceps"]
            ),
            ExerciseCatalogItem(
                id: "triceps_barra_v",
                name: "Tríceps Pulley com Barra V",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Cabeça Lateral e Medial com Conforto Articular",
                targetMuscles: "Tríceps Braquial",
                gifName: "triceps_barra_v",
                setupInstruction: "Prenda a barra V na polia alta. Segure na curvatura ergonômica com pegada neutra/semipronada.",
                eccentricInstruction: "Retorne a barra suavemente até os cotovelos formarem 90°.",
                concentricInstruction: "Empurre para baixo estendendo os braços com força.",
                breathingInstruction: "Expire na extensão e inspire no retorno.",
                commonMistake: "Balançar o corpo para frente.",
                synonyms: ["triceps barra v", "v bar pushdown", "triceps v"]
            ),
            ExerciseCatalogItem(
                id: "triceps_paralelas",
                name: "Paralelas com Foco em Tríceps",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Tríceps Braquial (Carga Corporal Avançada)",
                targetMuscles: "Tríceps Braquial, Peitoral, Deltoide Anterior",
                gifName: "triceps_paralelas",
                setupInstruction: "Suspenda-se nas paralelas mantendo o corpo vertical e tronco o mais ereto possível.",
                eccentricInstruction: "Desça dobrando os cotovelos para trás até 90° mantendo o tronco vertical.",
                concentricInstruction: "Empurre de volta estendendo os cotovelos com a força pura dos tríceps.",
                breathingInstruction: "Inspire na descida e expire ao subir.",
                commonMistake: "Inclinar o corpo para frente ativando o peito.",
                synonyms: ["paralelas triceps", "dips triceps", "mergulho paralelas"]
            ),
            ExerciseCatalogItem(
                id: "triceps_frances_polia",
                name: "Tríceps Francês na Polia com Corda",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Cabeça Longa sob Tensão Constante",
                targetMuscles: "Tríceps Braquial (Cabeça Longa)",
                gifName: "triceps_frances_polia",
                setupInstruction: "De costas para a polia alta ou média, segure a corda sobre a cabeça com cotovelos apontados para frente.",
                eccentricInstruction: "Dobre os cotovelos deixando a corda ir atrás da cabeça sentindo o alongamento do tríceps.",
                concentricInstruction: "Estenda os braços para frente e para cima abrindo a corda no final.",
                breathingInstruction: "Inspire na descida e expire na extensão.",
                commonMistake: "Deixar o cabo puxar os cotovelos para fora.",
                synonyms: ["triceps frances polia", "overhead cable extension", "frances cabo"]
            ),
            ExerciseCatalogItem(
                id: "triceps_kickback_cabo",
                name: "Tríceps Coice no Cabo / Polia",
                muscleGroup: "Tríceps",
                emphasisMuscle: "Isolamento Máximo da Cabeça Lateral",
                targetMuscles: "Tríceps Braquial",
                gifName: "triceps_kickback_cabo",
                setupInstruction: "Ajuste a polia média/baixa sem puxador (segurando a bolinha). Incline o tronco com cotovelo alto.",
                eccentricInstruction: "Retorne o braço de forma lenta mantendo o cotovelo fixo no espaço.",
                concentricInstruction: "Estenda o antebraço para trás até o alinhamento total, esmagando o tríceps.",
                breathingInstruction: "Expire na extensão e inspire na volta.",
                commonMistake: "Mover o cotovelo de posição.",
                synonyms: ["coice na polia", "cable kickback", "coice cabo"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_crunch",
                name: "Crunch Abdominal no Solo",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal (Porção Superior)",
                targetMuscles: "Reto Abdominal, Oblíquos",
                gifName: "abdominal_crunch",
                setupInstruction: "Deite-se de costas com joelhos dobrados e pés apoiados no chão. Mãos nas têmporas ou cruzadas no peito.",
                eccentricInstruction: "Desça o tronco devagar sem encostar totalmente as escápulas no chão para manter a tensão.",
                concentricInstruction: "Enrole o tronco aproximando as costelas da pelve, esvaziando o ar e contraindo o abdômen ao máximo.",
                breathingInstruction: "Expire completamente na subida (contração) e inspire na descida.",
                commonMistake: "Puxar a cabeça com as mãos forçando a coluna cervical.",
                synonyms: ["abdominal", "crunch", "abdominal tradicional", "abdominal solo"]
            ),
            ExerciseCatalogItem(
                id: "prancha",
                name: "Prancha Isométrica Tradicional",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Transverso do Abdômen e Core Profundo",
                targetMuscles: "Transverso Abdominal, Reto Abdominal, Oblíquos, Glúteos",
                gifName: "prancha",
                setupInstruction: "Apoie os antebraços e as pontas dos pés no chão. Cotovelos alinhados sob os ombros.",
                eccentricInstruction: "Mantenha a respiração estável sem deixar a pelve cair ou subir demais.",
                concentricInstruction: "Sustente a posição em linha reta dos calcanhares à cabeça, contraindo abdômen e glúteos com força contínua.",
                breathingInstruction: "Respiração curta e ritmada pelo diafragma sem perder a pressão intra-abdominal.",
                commonMistake: "Deixar o quadril cair em direção ao solo tensionando a lombar.",
                synonyms: ["prancha", "plank", "prancha isometrica", "prancha solo"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_infra",
                name: "Abdominal Infra / Elevação de Pernas",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal (Porção Inferior)",
                targetMuscles: "Reto Abdominal Inferior, Ilíaco, Reto Femoral",
                gifName: "abdominal_infra",
                setupInstruction: "Deite-se de costas no colchonete com as mãos ao lado do quadril. Pernas estendidas ou levemente flexionadas.",
                eccentricInstruction: "Desça as pernas devagar sem permitir que a coluna lombar se descole do chão.",
                concentricInstruction: "Eleve as pernas e no topo descole ligeiramente a pelve do chão enrolando a bacia para cima.",
                breathingInstruction: "Expire ao elevar as pernas e inspire ao descer.",
                commonMistake: "Arquear a lombar tirando o contato com o chão na descida das pernas.",
                synonyms: ["abdominal infra", "elevação de pernas", "leg raise", "infra"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_polia",
                name: "Abdominal na Polia com Corda",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal com Sobrecarga",
                targetMuscles: "Reto Abdominal, Oblíquos",
                gifName: "abdominal_polia",
                setupInstruction: "De joelhos de costas ou frente para a polia alta, segure a corda com as mãos ao lado das orelhas.",
                eccentricInstruction: "Suba o tronco com controle permitindo que a coluna volte à linha reta sem esticar excessivamente.",
                concentricInstruction: "Enrole a coluna puxando a corda para baixo com a força do abdômen, levando os cotovelos aos joelhos.",
                breathingInstruction: "Expire ao enrolar e inspire na subida.",
                commonMistake: "Flexionar o quadril como se estivesse sentando nos calcanhares em vez de enrolar a coluna.",
                synonyms: ["abdominal polia", "cable crunch", "abdominal cabo", "crunch na polia"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_remador",
                name: "Abdominal Remador Militar",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal Completo e Flexores do Quadril",
                targetMuscles: "Reto Abdominal, Ilipsoas, Oblíquos",
                gifName: "abdominal_remador",
                setupInstruction: "Deite-se totalmente estendido no chão com braços esticados atrás da cabeça e pernas estendidas.",
                eccentricInstruction: "Retorne estendendo todo o corpo no solo de forma controlada.",
                concentricInstruction: "Suba flexionando o tronco e abraçando os joelhos simultaneamente no topo.",
                breathingInstruction: "Expire ao abraçar os joelhos e inspire ao deitar.",
                commonMistake: "Bater as costas bruscamente no chão na descida.",
                synonyms: ["abdominal remador", "remador", "sit up militar"]
            ),
            ExerciseCatalogItem(
                id: "prancha_lateral",
                name: "Prancha Lateral Isométrica",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Oblíquos e Quadrado Lombar",
                targetMuscles: "Oblíquo Interno e Externo, Glúteo Médio",
                gifName: "prancha_lateral",
                setupInstruction: "Deite-se de lado apoiado sobre um antebraço, com o cotovelo diretamente sob o ombro e pernas alinhadas.",
                eccentricInstruction: "Sustente a posição de forma isométrica durante todo o tempo estipulado.",
                concentricInstruction: "Eleve o quadril do solo mantendo o corpo em linha reta perfeita dos pés à cabeça.",
                breathingInstruction: "Mantenha a respiração suave e constante.",
                commonMistake: "Deixar o quadril cair em direção ao chão ou rotacionar o tronco para a frente.",
                synonyms: ["prancha lateral", "side plank", "prancha obliquo"]
            ),
            ExerciseCatalogItem(
                id: "roda_abdominal",
                name: "Roda Abdominal (Ab Wheel Rollout)",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Parede Abdominal e Core Avançado",
                targetMuscles: "Reto Abdominal, Transverso, Dorsais, Ombros",
                gifName: "roda_abdominal",
                setupInstruction: "De joelhos com a roda no chão à frente, segurando as manoplas firmemente.",
                eccentricInstruction: "Role a roda para a frente estendendo o corpo o máximo possível sem deixar a lombar desabar.",
                concentricInstruction: "Puxe a roda de volta contraindo o abdômen e enrolando o corpo de volta aos joelhos.",
                breathingInstruction: "Inspire ao rolar para frente e expire na puxada de volta.",
                commonMistake: "Deixar a coluna lombar arquear no ponto mais distante.",
                synonyms: ["roda abdominal", "ab wheel", "rolinho abdominal", "rollout"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_bicicleta",
                name: "Abdominal Bicicleta / Cruzado",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Oblíquos e Reto Abdominal",
                targetMuscles: "Oblíquos Interno e Externo, Reto Abdominal",
                gifName: "abdominal_bicicleta",
                setupInstruction: "Deitado de costas, mãos atrás da cabeça, pernas elevadas com joelhos a 90°.",
                eccentricInstruction: "Mantenha o movimento rítmico e controlado sem puxar o pescoço.",
                concentricInstruction: "Leve o cotovelo direito em direção ao joelho esquerdo enquanto estende a perna direita, alternando os lados dinamicamente.",
                breathingInstruction: "Expire a cada rotação alternada.",
                commonMistake: "Fazer rápido demais sem focar na rotação do tronco.",
                synonyms: ["abdominal bicicleta", "bicycle crunch", "abdominal cruzado"]
            ),
            ExerciseCatalogItem(
                id: "elevacao_pernas_barra",
                name: "Elevação de Pernas Suspenso na Barra",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal Inferior e Força de Pegada",
                targetMuscles: "Reto Abdominal, Flexores do Quadril, Antebraços",
                gifName: "elevacao_pernas_barra",
                setupInstruction: "Suspenda-se na barra fixa com pegada pronada na largura dos ombros.",
                eccentricInstruction: "Desça as pernas de forma lenta e sem permitir que o corpo balance como um pêndulo.",
                concentricInstruction: "Eleve as pernas estendidas ou flexionadas até que fiquem paralelas ao chão ou alcancem a barra, enrolando a pelve.",
                breathingInstruction: "Expire na subida das pernas e inspire na descida.",
                commonMistake: "Usar balanço do corpo para jogar as pernas para cima.",
                synonyms: ["elevação de pernas barra", "hanging leg raise", "infra na barra"]
            ),
            ExerciseCatalogItem(
                id: "vacuum_abdominal",
                name: "Stomach Vacuum (Respiração Hipopressiva)",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Transverso Profundo do Abdômen",
                targetMuscles: "Transverso do Abdômen, Diafragma, Assoalho Pélvico",
                gifName: "vacuum_abdominal",
                setupInstruction: "Em pé com as mãos apoiadas nos joelhos ou em quatro apoios.",
                eccentricInstruction: "Sustente a sucção por 15 a 30 segundos em apneia antes de relaxar e inspirar.",
                concentricInstruction: "Solte todo o ar dos pulmões e puxe o umbigo o máximo possível em direção à coluna vertebral, criando um vácuo no abdômen.",
                breathingInstruction: "Expiração forçada prévia, retenção em apneia durante o vácuo e inspiração lenta ao término.",
                commonMistake: "Tentar fazer com o estômago cheio ou puxar pouco o umbigo.",
                synonyms: ["vacuum", "stomach vacuum", "vacuo abdominal", "hipopressivo"]
            ),
            ExerciseCatalogItem(
                id: "abdominal_declinado",
                name: "Abdominal no Banco Declinado",
                muscleGroup: "Abdômen",
                emphasisMuscle: "Reto Abdominal contra a Gravidade",
                targetMuscles: "Reto Abdominal, Oblíquos",
                gifName: "abdominal_declinado",
                setupInstruction: "Prenda os pés no suporte do banco declinado e deite-se com as mãos no peito.",
                eccentricInstruction: "Desça o tronco devagar até quase encostar no encosto.",
                concentricInstruction: "Suba enrolando a coluna até a contração máxima do abdômen.",
                breathingInstruction: "Expire na subida e inspire na descida.",
                commonMistake: "Subir com a coluna reta forçando os flexores do quadril em vez de enrolar o abdômen.",
                synonyms: ["abdominal declinado", "decline crunch", "sit up declinado"]
            ),
            // MARK: - Variações Adicionais do Catálogo de Vídeos 3D (Total: 82 movimentos)
            ExerciseCatalogItem(
                id: "puxada_triangulo",
                name: "Puxada Alta com Triângulo",
                muscleGroup: "Costas",
                emphasisMuscle: "Grande Dorsal Inferior (Pegada Neutra)",
                targetMuscles: "Grande Dorsal, Redondo Maior, Bíceps, Braquial",
                gifName: "puxada_triangulo",
                setupInstruction: "Conecte o puxador triângulo na polia alta, ajuste os rolos sobre as coxas e segure com pegada neutra.",
                eccentricInstruction: "Retorne controladamente estendendo os cotovelos acima da cabeça, permitindo o alongamento profundo das dorsais.",
                concentricInstruction: "Deprima as escápulas e puxe o triângulo em direção à parte superior do esterno, mantendo os cotovelos próximos ao tronco.",
                breathingInstruction: "Expire ao puxar o triângulo até o peito e inspire alongando na subida.",
                commonMistake: "Balançar excessivamente a lombar para trás transformando a puxada em remada.",
                synonyms: ["puxada triangulo", "puxada neutra", "pulley triangulo", "puxada fechada", "close grip pulldown"]
            ),
            ExerciseCatalogItem(
                id: "agachamento_frontal",
                name: "Agachamento Frontal com Barra",
                muscleGroup: "Pernas",
                emphasisMuscle: "Quadríceps (Ênfase Anterior e Core)",
                targetMuscles: "Quadríceps, Glúteo Máximo, Eretores Torácicos, Core",
                gifName: "agachamento_frontal",
                setupInstruction: "Apoie a barra sobre os deltoides anteriores (pegada clean ou braços cruzados) mantendo os cotovelos apontados bem à frente.",
                eccentricInstruction: "Inspire no bracing e desça com o tronco bem vertical, projetando os joelhos na direção da ponta dos pés.",
                concentricInstruction: "Empurre o solo com firmeza mantendo o peito alto e os cotovelos elevados até estender quadris e joelhos.",
                breathingInstruction: "Inspire antes de iniciar a descida e expire ao superar o ponto crítico da subida.",
                commonMistake: "Deixar os cotovelos caírem na subida, arredondando a coluna torácica.",
                synonyms: ["agachamento frontal", "front squat", "agachamento pela frente"]
            ),
            ExerciseCatalogItem(
                id: "agachamento_sumo",
                name: "Agachamento Sumô",
                muscleGroup: "Pernas",
                emphasisMuscle: "Adutores da Coxa e Glúteo Máximo",
                targetMuscles: "Adutores, Glúteo Máximo, Quadríceps",
                gifName: "agachamento_sumo",
                setupInstruction: "Afaste os pés além da largura dos ombros com as pontas voltadas cerca de 35° a 45° para fora.",
                eccentricInstruction: "Desça controlando o quadril verticalmente enquanto empurra ativamente os joelhos para fora, alinhados aos pés.",
                concentricInstruction: "Contraia glúteos e parte interna das coxas empurrando pelos calcanhares até retornar à extensão.",
                breathingInstruction: "Inspire descendo e expire contraindo glúteos e adutores no topo.",
                commonMistake: "Permitir valgo dinâmico (joelhos caindo para dentro em relação à ponta dos pés).",
                synonyms: ["agachamento sumo", "sumo squat", "sumo com haltere", "sumo barra"]
            ),
            ExerciseCatalogItem(
                id: "panturrilha_sentado",
                name: "Panturrilha Sentado (Sóleo)",
                muscleGroup: "Pernas",
                emphasisMuscle: "Músculo Sóleo (Joelho Flexionado 90°)",
                targetMuscles: "Sóleo, Gastrocnêmio",
                gifName: "panturrilha_sentado",
                setupInstruction: "Sente-se no aparelho de panturrilha com o apoio firme logo acima dos joelhos e a ponta dos pés na plataforma.",
                eccentricInstruction: "Desça os calcanhares de forma lenta abaixo da linha da plataforma por 2 segundos, alongando o tendão calcâneo.",
                concentricInstruction: "Eleve os calcanhares até a contração máxima na ponta dos pés e sustente 1 segundo no pico.",
                breathingInstruction: "Inspire alongando embaixo e expire no pico da flexão plantar.",
                commonMistake: "Executar movimentos curtos e rápidos usando rebote elástico.",
                synonyms: ["panturrilha sentado", "gemeos sentado", "soleo", "seated calf raise", "burrinho sentado"]
            ),
            ExerciseCatalogItem(
                id: "crucifixo_inverso_maquina",
                name: "Crucifixo Inverso na Máquina (Peck Deck)",
                muscleGroup: "Ombros",
                emphasisMuscle: "Deltoide Posterior Isolado",
                targetMuscles: "Deltoide Posterior, Infraespinhal, Trapézio Médio",
                gifName: "crucifixo_inverso_maquina",
                setupInstruction: "Ajuste o assento de frente para o encosto do Peck Deck para que as pegadas fiquem na linha horizontal dos ombros.",
                eccentricInstruction: "Retorne os braços à frente controladamente sem deixar as placas de peso baterem.",
                concentricInstruction: "Abra os braços em arco horizontal conduzindo pelos cotovelos até alinhar com o tronco, focando no posterior de ombro.",
                breathingInstruction: "Expire ao abrir os braços e inspire ao retornar à frente.",
                commonMistake: "Encolher o trapézio superior ou retrair demais as escápulas roubando tensão do deltoide posterior.",
                synonyms: ["crucifixo inverso maquina", "peck deck invertido", "voador invertido", "voador costas", "reverse pec deck"]
            ),
        ]
    }

    // MARK: - Hierarquia de Exercícios Principais (27) e Variações (55)

    /// Mapa canônico associando cada uma das 55 variações ao seu exercício principal
    public static let variationParentMap: [String: String] = [
        // Peitoral
        "supino_inclinado": "supino_reto",
        "supino_declinado": "supino_reto",
        "supino_reto_halteres": "supino_reto",
        "supino_inclinado_halteres": "supino_reto",
        "crucifixo_inclinado": "crucifixo",
        "crossover_polia": "crucifixo",
        "voador_peck_deck": "crucifixo",
        "paralelas_peito": "flexao_de_braco",
        // Costas
        "puxada_triangulo": "puxada_alta",
        "puxada_articulada": "puxada_alta",
        "pulldown_corda": "puxada_alta",
        "pull_over": "puxada_alta",
        "chin_up": "barra_fixa",
        "remada_baixa": "remada_curvada",
        "remada_unilateral": "remada_curvada",
        "remada_cavalinho": "remada_curvada",
        // Pernas
        "agachamento_frontal": "agachamento_livre",
        "agachamento_sumo": "agachamento_livre",
        "agachamento_hack": "agachamento_livre",
        "leg_press": "agachamento_livre",
        "agachamento_bulgaro": "afundo",
        "mesa_flexora": "cadeira_extensora",
        "cadeira_flexora": "cadeira_extensora",
        "panturrilha_sentado": "panturrilha",
        // Ombros
        "desenvolvimento_barra": "desenvolvimento",
        "desenvolvimento_arnold": "desenvolvimento",
        "elevacao_lateral_polia": "elevacao_lateral",
        "elevacao_lateral_inclinado": "elevacao_lateral",
        "elevacao_frontal": "elevacao_lateral",
        "crucifixo_inverso_maquina": "crucifixo_invertido",
        "face_pull": "crucifixo_invertido",
        "remada_alta": "encolhimento",
        // Bíceps
        "rosca_alternada": "rosca_direta",
        "rosca_inversa": "rosca_direta",
        "rosca_21": "rosca_direta",
        "rosca_martelo_corda": "rosca_martelo",
        "rosca_concentrada": "rosca_scott",
        "rosca_inclinada": "rosca_scott",
        "rosca_spider": "rosca_scott",
        "rosca_bayesian": "rosca_scott",
        // Tríceps
        "triceps_barra_reta": "triceps_corda",
        "triceps_barra_v": "triceps_corda",
        "supino_fechado": "triceps_testa",
        "triceps_frances_polia": "triceps_frances",
        "triceps_coice": "triceps_frances",
        "triceps_kickback_cabo": "triceps_frances",
        "mergulho_banco": "triceps_paralelas",
        // Abdômen
        "abdominal_polia": "abdominal_crunch",
        "abdominal_remador": "abdominal_crunch",
        "abdominal_bicicleta": "abdominal_crunch",
        "abdominal_declinado": "abdominal_crunch",
        "elevacao_pernas_barra": "abdominal_infra",
        "roda_abdominal": "abdominal_infra",
        "prancha_lateral": "prancha",
        "vacuum_abdominal": "prancha"
    ]

    /// Todos os 27 exercícios principais
    public var mainExercises: [ExerciseCatalogItem] {
        items.filter { !$0.isVariation }
    }

    /// Retorna o exercício principal correspondente a qualquer ID (principal ou variação)
    public func mainExercise(for itemId: String) -> ExerciseCatalogItem? {
        let rootId = Self.variationParentMap[itemId] ?? itemId
        return item(withId: rootId)
    }

    /// Retorna todas as variações associadas ao exercício principal do ID informado
    public func variations(for itemId: String) -> [ExerciseCatalogItem] {
        let rootId = Self.variationParentMap[itemId] ?? itemId
        return items.filter { $0.isVariation && $0.mainExerciseId == rootId }
    }

    /// Retorna a família completa (exercício principal + todas as suas variações), útil para explorar alternativas
    public func familyExercises(for itemId: String) -> [ExerciseCatalogItem] {
        let rootId = Self.variationParentMap[itemId] ?? itemId
        var result: [ExerciseCatalogItem] = []
        if let main = item(withId: rootId) {
            result.append(main)
        }
        result.append(contentsOf: items.filter { $0.isVariation && $0.mainExerciseId == rootId })
        return result
    }

    /// Retorna os exercícios de um determinado grupo muscular (principais ordenados junto às suas variações)
    public func items(for group: String) -> [ExerciseCatalogItem] {
        let groupItems = items.filter { $0.muscleGroup.lowercased() == group.lowercased() }
        let mains = groupItems.filter { !$0.isVariation }
        var organized: [ExerciseCatalogItem] = []
        for main in mains {
            organized.append(main)
            organized.append(contentsOf: groupItems.filter { $0.isVariation && $0.mainExerciseId == main.id })
        }
        // Segurança caso algum item do grupo tenha parent em outro grupo
        for extra in groupItems where !organized.contains(extra) {
            organized.append(extra)
        }
        return organized
    }

    // MARK: - Normalização e Associação Inteligente com IA (100% Cobertura de Vídeo)

    private static let stopwords: Set<String> = [
        "com", "sem", "para", "por", "nos", "nas", "dos", "das", "uma", "uns", "umas", "ate", "foco", "livre"
    ]

    private static let modifierGroups: [[String]] = [
        ["inclinado", "inclinada", "incline"],
        ["declinado", "declinada", "canadense", "decline"],
        ["halter", "halteres", "dumbbell", "db"],
        ["barra", "barbell"],
        ["polia", "pulley", "cabo", "cable", "crossover"],
        ["corda", "rope"],
        ["triangulo", "neutra", "fechada"],
        ["articulada", "articulado", "maquina", "machine"],
        ["peck", "voador"],
        ["unilateral", "serrote"],
        ["cavalinho"],
        ["frontal", "front"],
        ["sumo"],
        ["hack"],
        ["bulgaro", "bulgarian"],
        ["afundo", "passada", "avanco", "lunge"],
        ["sentado", "sentada", "cadeira", "seated"],
        ["deitado", "deitada", "mesa", "lying"],
        ["arnold"],
        ["militar", "military"],
        ["inverso", "inversa", "invertido", "invertida", "reverse"],
        ["scott", "preacher"],
        ["concentrada", "concentrado"],
        ["spider"],
        ["bayesian"],
        ["martelo", "hammer"],
        ["alternada", "alternado"],
        ["21", "vinte"],
        ["testa", "skull"],
        ["frances", "overhead"],
        ["coice", "kickback"],
        ["fechado", "close"],
        ["mergulho"],
        ["paralela", "paralelas", "dips"],
        ["remador"],
        ["bicicleta", "cruzado", "obliquo"],
        ["roda", "wheel", "abwheel"],
        ["infra"],
        ["suspenso", "hanging"],
        ["lateral"],
        ["vacuum", "vacuo", "hipopressivo"],
        ["prancha", "plank"],
        ["chin", "supinada"],
        ["stiff", "romeno", "rdl"],
        ["pelvica", "thrust", "gluteo"],
        ["extensora"],
        ["flexora"],
        ["panturrilha", "gemeos", "soleo", "calf"]
    ]

    private func normalize(_ raw: String) -> String {
        let folded = raw
            .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
            .lowercased()
        let cleaned = folded.unicodeScalars.map { CharacterSet.alphanumerics.contains($0) ? Character($0) : " " }
        return String(cleaned)
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    private func inferCanonicalGroup(from hint: String) -> String? {
        let h = normalize(hint)
        guard !h.isEmpty else { return nil }
        if h.contains("peito") || h.contains("peitoral") || h.contains("chest") { return "Peitoral" }
        if h.contains("costa") || h.contains("dorsal") || h.contains("romboide") || h.contains("lombar") || h.contains("back") { return "Costas" }
        if h.contains("perna") || h.contains("quadriceps") || h.contains("gluteo") || h.contains("isquio") || h.contains("posterior") || h.contains("coxa") || h.contains("adutor") || h.contains("panturrilha") || h.contains("soleo") || h.contains("gemeo") || h.contains("leg") { return "Pernas" }
        if h.contains("ombro") || h.contains("deltoide") || h.contains("trapezio") || h.contains("shoulder") { return "Ombros" }
        if h.contains("biceps") || h.contains("braquial") || h.contains("antebraco") { return "Bíceps" }
        if h.contains("triceps") { return "Tríceps" }
        if h.contains("abdom") || h.contains("core") || h.contains("obliquo") || h.contains("lombar") { return "Abdômen" }
        return nil
    }

    /// Encontra o exercício mais compatível a partir do texto do treino ou comando de voz da IA usando pontuação de especificidade
    public func matchExercise(query: String, targetMuscle: String = "") -> ExerciseCatalogItem? {
        let rawTrimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawTrimmed.isEmpty else { return nil }

        let idCandidate = rawTrimmed
            .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
            .lowercased()
            .replacingOccurrences(of: ".mp4", with: "")
            .replacingOccurrences(of: ".gif", with: "")
            .replacingOccurrences(of: "-", with: "_")
            .replacingOccurrences(of: " ", with: "_")

        // 1. Busca por ID canônico exato
        if let exact = items.first(where: { $0.id == idCandidate }) {
            return exact
        }

        let qNorm = normalize(rawTrimmed)
        guard !qNorm.isEmpty else { return nil }

        // 2. Igualdade exata com nome ou sinônimo completo
        for item in items {
            if normalize(item.name) == qNorm { return item }
            for syn in item.synonyms where normalize(syn) == qNorm {
                return item
            }
        }

        let qWords = qNorm.split(separator: " ").map(String.init)
        let qTokens = qWords.filter { ($0.count >= 3 || $0 == "21" || $0 == "db") && !Self.stopwords.contains($0) }
        let hintedGroup = inferCanonicalGroup(from: targetMuscle) ?? inferCanonicalGroup(from: rawTrimmed)

        var bestItem: ExerciseCatalogItem? = nil
        var bestScore: Int = 0

        for item in items {
            var score = 0
            let nameNorm = normalize(item.name)
            let idNorm = item.id.replacingOccurrences(of: "_", with: " ")
            let synsNorm = item.synonyms.map { normalize($0) }
            let fullBlob = ([nameNorm, idNorm] + synsNorm).joined(separator: " ")
            let itemWordSet = Set(fullBlob.split(separator: " ").map(String.init))

            // Pontuação pelo sinônimo mais longo e específico contido na query
            var bestSynBonus = 0
            for syn in synsNorm where !syn.isEmpty {
                let paddedQ = " \(qNorm) "
                let paddedSyn = " \(syn) "
                if paddedQ.contains(paddedSyn) {
                    bestSynBonus = max(bestSynBonus, 36 + syn.count * 3)
                } else if paddedSyn.contains(paddedQ) && qNorm.count >= 5 {
                    bestSynBonus = max(bestSynBonus, 24 + qNorm.count * 2)
                }
            }
            score += bestSynBonus

            // Sobreposição de tokens relevantes
            for tok in qTokens {
                if itemWordSet.contains(tok) {
                    score += 20 + tok.count * 2
                } else if fullBlob.contains(tok) && tok.count >= 4 {
                    score += 12 + tok.count
                }
            }

            // Modificadores de variação biomecânica (inclinado, halteres, polia, sumo, bayesian, etc.)
            var queryHasSpecificModifier = false
            for modGroup in Self.modifierGroups {
                let qHas = modGroup.contains { qTokens.contains($0) || " \(qNorm) ".contains(" \($0) ") }
                if qHas {
                    queryHasSpecificModifier = true
                    let itemHas = modGroup.contains { itemWordSet.contains($0) || " \(fullBlob) ".contains(" \($0) ") }
                    if itemHas {
                        score += 38
                    } else {
                        score -= 10
                    }
                }
            }

            // Se a busca não pediu variação específica, prioriza o Exercício Principal daquela família
            if !queryHasSpecificModifier && !item.isVariation && score > 0 {
                score += 10
            }

            // Alinhamento com o grupo muscular alvo informado pela IA
            if let g = hintedGroup, item.muscleGroup == g {
                score += (score > 0) ? 24 : 8
            }

            if score > bestScore {
                bestScore = score
                bestItem = item
            }
        }

        if bestScore >= 20, let winner = bestItem {
            return winner
        }

        // Fallback por grupo muscular se o nome for muito genérico (ex: "Treino de Peito", "Exercício Dorsal")
        if let g = hintedGroup {
            return defaultMainExercise(forMuscleGroup: g)
        }

        return nil
    }

    /// Retorna o exercício principal padrão de um grupo muscular para garantia de vídeo 100%
    public func defaultMainExercise(forMuscleGroup group: String) -> ExerciseCatalogItem {
        let canonicalDefaults: [String: String] = [
            "Peitoral": "supino_reto",
            "Costas": "puxada_alta",
            "Pernas": "agachamento_livre",
            "Ombros": "desenvolvimento",
            "Bíceps": "rosca_direta",
            "Tríceps": "triceps_corda",
            "Abdômen": "abdominal_crunch"
        ]
        if let id = canonicalDefaults[group], let found = item(withId: id) {
            return found
        }
        return items.first { $0.muscleGroup.lowercased() == group.lowercased() } ?? items[0]
    }

    /// Garante SEMPRE um exercício válido com vídeo 3D de 10s (usado na criação de exercícios/treinos com IA)
    public func guaranteedMatch(for name: String, targetMuscle: String = "") -> ExerciseCatalogItem {
        if let matched = matchExercise(query: name, targetMuscle: targetMuscle) {
            return matched
        }
        if let group = inferCanonicalGroup(from: "\(name) \(targetMuscle)") {
            return defaultMainExercise(forMuscleGroup: group)
        }
        return item(withId: "agachamento_livre") ?? items[0]
    }

    /// Busca o melhor exercício compatível a partir do nome (alias de matchExercise)
    public func findBestMatch(for name: String) -> ExerciseCatalogItem? {
        return matchExercise(query: name)
    }

    /// Associa SEMPRE um vídeo de execução válido aos exercícios criados manualmente ou via IA
    public func autoAssociateExercises(in exercises: [WorkoutExerciseItem]) -> [WorkoutExerciseItem] {
        var updated = exercises
        for i in 0..<updated.count {
            // Valida se o animationId já existe de fato no catálogo de 82 vídeos
            if let existingId = updated[i].animationId,
               !existingId.isEmpty,
               let validItem = item(withId: existingId) {
                if updated[i].executionTip.isEmpty {
                    updated[i].executionTip = validItem.concentricInstruction
                }
                continue
            }

            // Caso animationId esteja nil, vazio ou inválido (alucinado pela IA), resolve garantidamente para o melhor vídeo 3D!
            let candidateQuery = updated[i].animationId?.isEmpty == false
                ? "\(updated[i].name) \(updated[i].animationId!)"
                : updated[i].name
            let match = guaranteedMatch(for: candidateQuery, targetMuscle: updated[i].targetMuscle)
            updated[i].animationId = match.id
            if updated[i].executionTip.isEmpty {
                updated[i].executionTip = match.concentricInstruction
            }
        }
        return updated
    }

    /// Retorna os exercícios com os IDs de vídeo correspondentes preenchidos automaticamente
    public func enrichExercises(_ exercises: [WorkoutExerciseItem]) -> [WorkoutExerciseItem] {
        return autoAssociateExercises(in: exercises)
    }

    /// Retorna a rotina com todos os vídeos de execução associados garantidamente
    func enrichRoutineWithCatalog(_ routine: WorkoutRoutine) -> WorkoutRoutine {
        routine.exercises = autoAssociateExercises(in: routine.exercises)
        return routine
    }

    /// Retorna um item pelo seu ID
    public func item(withId id: String?) -> ExerciseCatalogItem? {
        guard let id = id?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty else { return nil }
        let clean = id
            .replacingOccurrences(of: ".mp4", with: "")
            .replacingOccurrences(of: ".gif", with: "")
        return items.first { $0.id == clean }
    }
}
