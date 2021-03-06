package letrando

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.control.Alert
import javafx.scene.control.TextField
import javafx.scene.media.AudioClip
import javafx.scene.paint.Color
import tornadofx.*
import khttp.*;
import java.util.*

data class Player(var name :String?= null, var date : String?=null, var score : Int ?= null)
data class Words(var numeroJogo :Int ?= null, var words :Array<String> ?= null)


class Letrando:App(MyView::class)

var allPlayersListSorted = listOf<Player>()  //lista contendo todos os players que estão no banco (definida globalmente para a tableview)
var allPlayersList = mutableListOf<Player>()  //lista contendo todos os players que estão no banco (definida globalmente para a tableview)
var allGameWords = mutableListOf<JsonElement>()
var player = String()

class dbController(){
    init {}
    fun getRecords(){
        val resp = get("http://localhost:8080/records")
        val allPlayers = resp.text
        val parser = JsonParser()
        val allPlayersJson = parser.parse(allPlayers)
        var allPlayersJsonArray = allPlayersJson.asJsonArray   //utilizo como JsonArray
        allPlayersList.clear()
        for (i in 0 until allPlayersJsonArray.size()){     //obtenho cada um dos elementos do JsonArray e adiciono na lista (AllPlayersList)
            var playerAux = Player()
            playerAux.name = allPlayersJsonArray.get(i).asJsonObject.get("name").asString
            playerAux.score = allPlayersJsonArray.get(i).asJsonObject.get("score").asInt
            playerAux.date = allPlayersJsonArray.get(i).asJsonObject.get("date").asString
            allPlayersList.add(playerAux)
        }
        allPlayersListSorted = allPlayersList.sortedBy { it.score }.reversed()
    }
    fun getWords(): JsonArray {
        val resp = get("http://localhost:8080/words")
        val allWords = resp.text
        val parser = JsonParser()
        val allWordsJson = parser.parse(allWords)
        var allWordsJsonArray = allWordsJson.asJsonArray
        allGameWords.clear()
   /*     println(allWordsJsonArray.get(0).asJsonObject.get("words"))
        var teste = allWordsJsonArray.get(0).asJsonObject.get("words").asJsonArray
        println(teste.get(0))*/
        var rand = Random().nextInt(2)
        return allWordsJsonArray.get(rand).asJsonObject.get("words").asJsonArray
    }

}

class Records:View(){       //view para mostrar as pontuações
    override val root = vbox()
    init {
        with(root){
            tableview(allPlayersListSorted.observable()){
                readonlyColumn("Nome",Player::name)
                readonlyColumn("Score",Player::score)
                readonlyColumn("Data",Player::date)
                columnResizePolicy = SmartResize.POLICY
            }
            hbox {
                button ("Ok") {
                    action {
                        close()
                    }
                }
            }
        }
    }
}

class MyView:View() {        //view inicial do jogo
    override val root = vbox()  //linha padrão do tornadofx, vbox(preenche com componentes verticalmente) pode ser substituido por outras views

    init {
        val audio = AudioClip(MyView::class.java.getResource("/medias/bgmusic.wav").toExternalForm())
        with(root) {
            audio.play()
            setPrefSize(800.0, 600.0)    //seta o tamanho da janela
            style {
                backgroundColor += c("#88ff88")
            }
            label("Letrando") {
                paddingLeft = 150
                paddingTop = 100
                textFill = Color.RED    //cor do texto
                val custom = loadFont("/font/madpakkeDEMO.otf", size = 140)
                font = custom
            }
            hbox {
                //horizontal box, preenche com componentes horizontalmente
                paddingTop = 30
                paddingLeft = 110
                label("Digite seu nome:") {
                    style {
                        fontSize = 24.px
                    }
                }
                val playerToVerify = object : ViewModel() {
                    val name = bind { SimpleStringProperty() }
                }
                textfield(playerToVerify.name) {}
                button("Jogar") {
                    action {
                        playerToVerify.commit {
                            var p = org.bson.Document()
                            p.append("name", playerToVerify.name.value)
                            var playerJson = p.toJson()
                            val post = post("http://localhost:8080/verify", data = playerJson)
                            if (post.statusCode == 400) {
                                alert(Alert.AlertType.ERROR, "Nome já utilizado", "Por favor, insira outro nome")
                            }
                            //verificação se o nome já existe aqui
                            else {
                                player = playerToVerify.name.value
                                var controller = dbController()
                                allGameWords = controller.getWords().toMutableList()

                                replaceWith<Game>()
                            }
                        }
                    }

                }
                button("Mostrar pontuações") {
                    action {
                        var controller = dbController()
                        controller.getRecords()
                        openInternalWindow<Records>()
                    }
                }
            }
        }
    }
}

private fun removeUltimoChar(str: String): String {
    return str.substring(0, str.length - 1)
}

class Game : View() {       //view do jogo
    override val root = vbox()

    init {
        with(root) {

            println(allGameWords)
            println(player)
            var acertos = FXCollections.observableArrayList("Palavras:")
            var palavraDoJogo = allGameWords.first().asString.toMutableList().shuffled()
            var tentativa = ""
            var pontuacao = 0

            style {
                backgroundColor += c("#88ff88")
            }

            menubar {
                menu("Opções") {
                    item("Desistir").action {
                        val playerScore = object : ViewModel() {
                            val name = player
                            val score = pontuacao
                        }
                        playerScore.commit {
                            var p = org.bson.Document()
                            p.append("name", playerScore.name)
                            p.append("score", playerScore.score)
                            var playerJson = p.toJson()
                            post("http://localhost:8080/playerScore", data = playerJson)

                        }
                        alert(Alert.AlertType.CONFIRMATION, "Fim de jogo!")
                        //close()
                        replaceWith<MyView>()

                    }
                }
            }

            stackpane {
                vbox {
                    label {
                        children.bind(acertos) {
                            label(it) {
                                style {
                                    fontSize = 25.px
                                }
                            }
                        }
                    }
                }

                vbox {
                    val campoTexto = label() {
                        paddingLeft = 300
                        paddingTop = 300
                        style {
                            fontSize = 50.px
                        }
                    }

                    hbox {
                        paddingLeft = 160
                        paddingTop = 10

                        for (letra in palavraDoJogo) {
                            togglebutton(letra.toString().capitalize()) {
                                paddingAll = 20
                                action {
                                    if (!isSelected) {
                                        tentativa += letra.toString()
                                        campoTexto.setText(tentativa)
                                        println(tentativa)
                                    } else {
                                        tentativa = removeUltimoChar(tentativa)
                                        campoTexto.setText(tentativa)
                                        println(tentativa)
                                    }
                                }
                            }
                        }

                        button("Enviar") {
                            paddingAll = 20
                            action {

                                val iterator = allGameWords.iterator()
                                var campo = campoTexto.getText()

                                while (iterator.hasNext()) {
                                    val item = iterator.next().toString().replace("\"", "")
                                    if (item == campo) {
                                        acertos.add(item)
                                        iterator.remove()
                                        println("Acertou")
                                        pontuacao++
                                        if (allGameWords.isEmpty()) {
                                            alert(Alert.AlertType.INFORMATION, "Fim de jogo!")
                                            val playerScore = object : ViewModel() {
                                                val name = player
                                                val score = pontuacao
                                            }
                                            playerScore.commit {
                                                var p = org.bson.Document()
                                                p.append("name", playerScore.name)
                                                p.append("score", playerScore.score)
                                                var playerJson = p.toJson()
                                                post("http://localhost:8080/playerScore", data = playerJson)

                                            }
                                            replaceWith<MyView>()
                                        }
                                        break
                                    } else {
                                        if (!iterator.hasNext())
                                            println("Errou")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>) {

}