import { Link } from 'react-router'
import { CONTROLLER } from './legal'
import { LegalLayout, List, Section } from './LegalLayout'

/** Termos de uso. Mudou o conteúdo? Nova `LEGAL_VERSION` (API e web). */
export function TermsPage() {
  const mail = <a href={`mailto:${CONTROLLER.email}`} className="font-medium text-foreground underline underline-offset-4">{CONTROLLER.email}</a>
  const privacy = <Link to="/privacidade" className="font-medium text-foreground underline underline-offset-4">Política de privacidade</Link>

  return (
    <LegalLayout
      title="Termos de uso"
      summary="Em resumo: o LifePedia é uma ferramenta pessoal para organizar finanças, tarefas e grupos. Seus dados são
        seus. Use com responsabilidade, respeite as outras pessoas dos seus grupos e lembre que os números do app são
        para organização, não são aconselhamento financeiro."
    >
      <Section title="1. Aceite">
        <p>
          Estes termos regem o uso do LifePedia, mantido por <strong>{CONTROLLER.name}</strong>. Ao criar uma conta ou
          usar o serviço, você declara que leu e concorda com estes termos e com a {privacy}. Se não concordar, não
          use o serviço.
        </p>
      </Section>

      <Section title="2. Quem pode usar">
        <p>
          Você precisa ter <strong>18 anos ou mais</strong> e capacidade para aceitar estes termos. Cada conta é
          pessoal e intransferível.
        </p>
      </Section>

      <Section title="3. Sua conta">
        <List>
          <li>Informe dados verdadeiros e mantenha-os atualizados.</li>
          <li>Guarde sua senha com cuidado e não a compartilhe. Você é responsável pelo que for feito com a sua conta.</li>
          <li>Suspeita de acesso indevido? Troque a senha, saia dos outros aparelhos e fale com a gente.</li>
        </List>
      </Section>

      <Section title="4. O que o LifePedia oferece">
        <p>
          Ferramentas para registrar e acompanhar finanças pessoais, tarefas, hábitos e agenda, organizar grupos (com
          finanças, planejamento, tarefas e chat) e se conectar com amigos. O serviço pode ganhar, mudar ou perder
          funcionalidades com o tempo. Mudanças que afetem seus dados serão avisadas no app.
        </p>
      </Section>

      <Section title="5. Seus dados e seu conteúdo">
        <List>
          <li>O que você registra continua sendo <strong>seu</strong>. Você nos autoriza apenas a guardar, processar e
            exibir esse conteúdo para prestar o serviço a você e a quem você escolher compartilhar.</li>
          <li>Em <strong>grupos</strong>, o que você leva ou escreve fica visível para os membros. Ao sair do grupo, seus
            itens saem com você; as mensagens do chat continuam no grupo até você apagá-las ou excluir a conta.</li>
          <li>Você pode baixar ou apagar seus dados a qualquer momento em <em>Meu perfil</em>. Recomendamos exportar uma
            cópia de vez em quando.</li>
          <li>Só registre dados de outras pessoas (por exemplo, em observações ou no chat) quando tiver o direito de
            fazê-lo.</li>
        </List>
      </Section>

      <Section title="6. Uso permitido">
        <p>Não é permitido:</p>
        <List>
          <li>usar o serviço para fins ilegais, fraude ou lavagem de dinheiro;</li>
          <li>assediar, ameaçar ou expor outras pessoas, ou publicar conteúdo ofensivo, discriminatório ou ilegal;</li>
          <li>enviar spam, convites ou pedidos de amizade em massa;</li>
          <li>tentar acessar contas ou dados de outras pessoas, burlar limites de segurança, explorar falhas ou
            sobrecarregar o serviço (inclusive com robôs);</li>
          <li>copiar, revender ou explorar comercialmente o serviço sem autorização.</li>
        </List>
        <p>
          Encontrou uma falha de segurança? Avise por {mail} sem explorá-la. Agradecemos de verdade.
        </p>
      </Section>

      <Section title="7. Informações financeiras e de terceiros">
        <List>
          <li>Resumos, saldos, simulações e planejamentos são calculados a partir do que você informa e servem para
            <strong> organização</strong>. Não são consultoria financeira, contábil ou de investimentos.</li>
          <li>Preços e fotos buscados em lojas ou no Buscapé podem estar desatualizados ou ser de um produto parecido; o
            app sempre indica a origem. Confira na loja antes de comprar.</li>
          <li>Na importação com IA, o serviço de IA é escolhido e usado por você. Revise a prévia antes de importar.</li>
        </List>
      </Section>

      <Section title="8. Disponibilidade">
        <p>
          Fazemos o possível para manter o LifePedia no ar e seus dados seguros, mas o serviço pode ficar indisponível
          por manutenção, falhas técnicas ou motivos fora do nosso controle. Não garantimos funcionamento ininterrupto
          ou livre de erros.
        </p>
      </Section>

      <Section title="9. Responsabilidade">
        <p>
          Na extensão permitida pela lei, não respondemos por decisões tomadas com base nas informações do app, por
          conteúdo publicado por outros usuários nem por danos causados por uso indevido da sua conta por falta de
          cuidado com a senha. Nada nestes termos afasta direitos garantidos a você pelo Código de Defesa do Consumidor
          ou pela LGPD.
        </p>
      </Section>

      <Section title="10. Encerramento">
        <List>
          <li>Você pode excluir sua conta quando quiser, em <em>Meu perfil</em>.</li>
          <li>Podemos suspender ou encerrar contas que violem estes termos ou a lei, avisando o motivo quando possível.
            Nesses casos você poderá pedir uma cópia dos seus dados, salvo impedimento legal.</li>
        </List>
      </Section>

      <Section title="11. Mudanças nestes termos">
        <p>
          Quando estes termos mudarem de forma relevante, a nova versão aparece aqui com a data, e o app pede que você a
          aceite antes de continuar. Se não concordar, você pode exportar seus dados e excluir a conta.
        </p>
      </Section>

      <Section title="12. Lei e foro">
        <p>
          Estes termos seguem as leis do Brasil. Fica eleito o foro do seu domicílio para resolver qualquer questão.
        </p>
      </Section>

      <Section title="13. Contato">
        <p>{CONTROLLER.name}, {mail}.</p>
      </Section>
    </LegalLayout>
  )
}
