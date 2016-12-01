package circlet.login

import circlet.reactive.*
import circlet.utils.*
import com.intellij.icons.*
import com.intellij.openapi.ui.*
import java.awt.*
import javax.swing.*
import javax.swing.border.*
import javax.swing.event.*

class LoginDialog(val viewModel: LoginDialogViewModel) : DialogWrapper(JOptionPane.getRootFrame(), true), DocumentListener {

    val loginField = JTextField(30)
    val passwordField = JPasswordField(30)

    init {
        isModal = true
        title = "Circlet - login"
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = loginField

    override fun createActions(): Array<Action> = arrayOf(helpAction, okAction, cancelAction)

    override fun createCenterPanel(): JComponent? {
        return JPanel(BorderLayout()).apply {
            border = EmptyBorder(5.px, 5.px, 5.px, 5.px)
            add(JLabel().apply {
                viewModel.loginStatus.bind(viewModel.lifetime, { status ->
                    text = status.presentStatus()
                    icon = when (status.status) {
                        LoginStatus.Fail -> AllIcons.General.Error
                        LoginStatus.Success -> AllIcons.General.InspectionsOK
                        LoginStatus.InProrgess -> null
                    }
                })
            }, BorderLayout.CENTER)
        }
    }

    override fun createNorthPanel(): JComponent? {
        val cs = GridBagConstraints()
        cs.fill = GridBagConstraints.HORIZONTAL

        return JPanel(GridBagLayout()).apply {
            add(JLabel().apply {
                cs.gridx = 0
                cs.gridy = 0
                cs.gridwidth = 1
                cs.weightx = 0.0
                cs.fill = GridBagConstraints.NONE
                cs.anchor = GridBagConstraints.LINE_START
                cs.insets = Insets(5.px, 5.px, 5.px, 5.px)
                text = "Login:"
            }, cs)

            add(loginField.apply {
                cs.gridx = 1
                cs.gridy = 0
                cs.weightx = 1.0
                cs.gridwidth = 2
                cs.insets = Insets(5.px, 0.px, 5.px, 5.px)
                cs.fill = GridBagConstraints.HORIZONTAL
                text = viewModel.login.value
                document.addDocumentListener(this@LoginDialog)
            }, cs)

            add(JLabel().apply {
                cs.gridx = 0
                cs.gridy = 1
                cs.gridwidth = 1
                cs.weightx = 0.0
                cs.anchor = GridBagConstraints.LINE_START
                cs.insets = Insets(5.px, 5.px, 5.px, 5.px)
                cs.fill = GridBagConstraints.NONE
                text = "Password:"
            }, cs)

            add(passwordField.apply {
                cs.gridx = 1
                cs.gridy = 1
                cs.gridwidth = 2
                cs.weightx = 1.0
                cs.insets = Insets(5.px, 0.px, 5.px, 5.px)
                cs.fill = GridBagConstraints.HORIZONTAL
                text = viewModel.pass.value
                document.addDocumentListener(this@LoginDialog)
            }, cs)

        }
    }

    override fun doOKAction() {
        viewModel.commit()
        dispose()
    }

    private fun refresh() {
        viewModel.login.value = loginField.text
        viewModel.pass.value = passwordField.text
    }

    override fun changedUpdate(e: DocumentEvent?) = refresh()
    override fun insertUpdate(e: DocumentEvent?) = refresh()
    override fun removeUpdate(e: DocumentEvent?) = refresh()

    override fun dispose() {
        viewModel.lifetimeDef.terminate()
        super.dispose()
    }
}
