import { Link } from 'react-router'
import { CONTROLLER } from './legal'
import { LegalLayout, List, Section } from './LegalLayout'

/** Política de privacidade (LGPD, Lei 13.709/2018). Mudou o conteúdo? Nova `LEGAL_VERSION` (API e web). */
export function PrivacyPage() {
  const mail = <a href={`mailto:${CONTROLLER.email}`} className="font-medium text-foreground underline underline-offset-4">{CONTROLLER.email}</a>

  return (
    <LegalLayout
      title="Política de privacidade"
      summary="Em resumo: usamos seus dados só para o LifePedia funcionar para você e para as pessoas com quem você escolhe
        compartilhar (grupos e amigos). Não vendemos dados, não mostramos anúncios e não usamos rastreadores. Você pode
        ver, corrigir, baixar e apagar seus dados a qualquer momento pelo próprio app."
    >
      <Section title="1. Quem cuida dos seus dados">
        <p>
          O LifePedia é mantido por <strong>{CONTROLLER.name}</strong>, que é o <strong>controlador</strong> dos dados
          pessoais tratados no serviço, nos termos da Lei Geral de Proteção de Dados (LGPD, Lei nº 13.709/2018). O mesmo
          responsável atua como <strong>encarregado</strong> (canal com os titulares e com a ANPD), pelo e-mail {mail}.
        </p>
      </Section>

      <Section title="2. Quais dados tratamos">
        <p><strong>Cadastro e conta</strong></p>
        <List>
          <li>Nome, nome de usuário (@), e-mail e senha. A senha nunca é guardada: guardamos só um resumo criptográfico
            (hash BCrypt) que não permite recuperá-la.</li>
          <li>Foto de perfil, se você enviar.</li>
          <li>Se você entrar com o Google: o identificador da sua conta Google, o e-mail e o nome que o Google informa.</li>
          <li>Data e versão em que você aceitou estes documentos.</li>
        </List>
        <p><strong>O que você registra no app</strong></p>
        <List>
          <li>Finanças: ganhos e despesas fixas, lançamentos, categorias, ajustes do mês, investimentos e os dados que
            você importa de extratos e faturas (inclusive a descrição original de cada lançamento).</li>
          <li>Tarefas, hábitos, agenda, listas, etiquetas, lembretes e comentários.</li>
          <li>Grupos: nome, imagem, membros, itens financeiros que você leva, planejamento (com links e fotos de produtos)
            e mensagens do chat.</li>
          <li>Amigos, pedidos de amizade, conquistas e notificações.</li>
        </List>
        <p><strong>Dados técnicos</strong></p>
        <List>
          <li>Sessões de login (identificador e validade), para manter você conectado e permitir sair de qualquer
            aparelho.</li>
          <li>Endereço IP, usado na hora para limitar tentativas de login (proteção contra invasão) e registrado nos
            logs de acesso do servidor (IP, data e hora, página pedida e navegador).</li>
          <li>Se você ativar as notificações no aparelho: o endereço de entrega e as chaves fornecidas pelo navegador.</li>
        </List>
        <p>
          Não pedimos dados sensíveis (saúde, religião, biometria etc.). Evite escrevê-los em campos livres, como
          observações e mensagens.
        </p>
      </Section>

      <Section title="3. Para que usamos e com qual base legal">
        <List>
          <li><strong>Prestar o serviço</strong> que você pediu: criar a conta, guardar e mostrar seus registros, calcular
            resumos, gerar lembretes e notificações, compartilhar com grupos e amigos. Base: execução de contrato
            (art. 7º, V).</li>
          <li><strong>Segurança</strong>: limitar tentativas de login, encerrar sessões, investigar abusos e incidentes.
            Base: legítimo interesse (art. 7º, IX), limitado ao necessário para proteger você e o
            serviço.</li>
          <li><strong>Login com o Google e notificações push</strong>: só se você escolher usar. Base: consentimento
            (art. 7º, I), que você retira a qualquer momento desvinculando o Google ou desativando as notificações em
            <em> Meu perfil</em>.</li>
          <li><strong>Obrigações legais</strong> e defesa de direitos, quando houver (art. 7º, II e VI), como guardar
            registros de acesso pelo prazo exigido pelo Marco Civil da Internet, quando aplicável.</li>
        </List>
        <p>Não usamos seus dados para publicidade, perfilamento comercial ou decisões automatizadas que afetem você.</p>
      </Section>

      <Section title="4. Com quem os dados são compartilhados">
        <List>
          <li><strong>Pessoas que você escolhe</strong>: membros dos seus grupos veem seu nome, @, foto e o que você leva
            ou escreve no grupo. Amigos veem seu perfil e suas conquistas (nunca valores financeiros). Qualquer pessoa
            com conta pode encontrar você pelo nome ou @ ao convidar ou adicionar amigos (só nome, @ e foto, nunca o
            e-mail).</li>
          <li><strong>Hospedagem</strong>: o provedor de servidor onde o LifePedia funciona armazena os dados em nosso
            nome, como operador.</li>
          <li><strong>Google</strong>: se você usar o botão do Google, a página carrega o script do Google e o Google
            sabe que você entrou no LifePedia, conforme a política de privacidade dele.</li>
          <li><strong>Serviços de notificação</strong> do seu navegador (Google, Mozilla, Apple ou Microsoft), se você
            ativar o push. O conteúdo vai <strong>criptografado de ponta a ponta</strong>: esses serviços só entregam,
            não conseguem ler.</li>
          <li><strong>Lojas e Buscapé</strong>: ao colar o link de um produto no planejamento, nosso servidor acessa a
            página da loja e, se ela não informar preço ou foto, busca o nome do produto no Buscapé. Nenhum dado seu é
            enviado além do link e do nome do produto.</li>
          <li><strong>Autoridades</strong>, somente quando a lei ou uma ordem judicial exigir.</li>
        </List>
        <p>
          <strong>Importação com IA</strong>: o LifePedia não envia seus extratos a nenhuma IA. Você copia o texto
          gerado e o leva, por conta própria, ao serviço de IA que escolher, que trata os dados conforme as regras dele.
          Oculte dados do cartão e documentos antes de enviar.
        </p>
        <p>Não vendemos, alugamos ou cedemos seus dados a terceiros.</p>
      </Section>

      <Section title="5. Transferência internacional">
        <p>
          Google, os serviços de notificação dos navegadores e o provedor de hospedagem podem processar dados fora do
          Brasil. Nesses casos a transferência ocorre para prestar o serviço que você pediu (art. 33, V e IX), com
          fornecedores que adotam padrões de proteção compatíveis com a LGPD.
        </p>
      </Section>

      <Section title="6. Cookies e armazenamento no navegador">
        <List>
          <li><strong>Cookie de sessão</strong> (essencial): mantém você conectado. É protegido (HttpOnly, Secure,
            SameSite) e não pode ser lido por scripts.</li>
          <li><strong>Preferências de tela</strong> (tema, menu recolhido, dias recolhidos na semana, cores da agenda),
            guardadas só no seu navegador.</li>
        </List>
        <p>Não usamos cookies de publicidade, de análise ou de terceiros, por isso não pedimos consentimento para eles.</p>
      </Section>

      <Section title="7. Por quanto tempo guardamos">
        <List>
          <li>Seus dados ficam enquanto sua conta existir. Você pode apagar itens a qualquer momento.</li>
          <li><strong>Ao excluir a conta</strong>, apagamos na hora seus dados, fotos, mensagens do chat, sessões e
            inscrições de notificação. Em cada grupo, seus itens saem como se você tivesse saído do grupo.</li>
          <li>Sessões vencidas são apagadas todos os dias. Ao sair da conta, o aparelho para de receber notificações.</li>
          <li>Logs de acesso do servidor são mantidos só pelo tempo necessário à segurança ou exigido por lei.</li>
          <li>Notificações já entregues a outras pessoas guardam o nome que você tinha na época (por exemplo, um convite
            antigo).</li>
        </List>
      </Section>

      <Section title="8. Como protegemos">
        <List>
          <li>Conexão criptografada (HTTPS) e senhas com hash BCrypt.</li>
          <li>Cada dado é ligado ao seu dono e só aparece para ele ou para quem ele compartilhou.</li>
          <li>Limite de tentativas de login, bloqueio temporário após senhas erradas e sessões que podem ser encerradas.</li>
          <li>Proteções contra ataques comuns da web (CSP, CSRF, clickjacking) e servidores sem privilégios de
            administrador.</li>
          <li>O app instalado não guarda seus dados no aparelho; tudo vem do servidor a cada acesso.</li>
        </List>
        <p>
          Nenhum sistema é totalmente imune. Se houver um incidente de segurança que possa causar risco ou dano
          relevante, avisaremos você e a Autoridade Nacional de Proteção de Dados (ANPD), como manda a LGPD (art. 48).
        </p>
      </Section>

      <Section title="9. Seus direitos">
        <p>A LGPD (art. 18) garante a você, a qualquer momento:</p>
        <List>
          <li><strong>Confirmação e acesso</strong> aos dados: em <Link to="/perfil" className="underline underline-offset-4">Meu perfil</Link>,
            <em> Meus dados</em> baixa tudo em JSON ou planilha.</li>
          <li><strong>Correção</strong>: edite o perfil e seus registros direto no app.</li>
          <li><strong>Portabilidade</strong>: o arquivo exportado pode ser importado em outra conta ou usado em outro
            serviço.</li>
          <li><strong>Eliminação</strong>: <em>Excluir conta</em> em Meu perfil apaga seus dados.</li>
          <li><strong>Revogar consentimento</strong>: desvincule o Google ou desative as notificações em Meu perfil.</li>
          <li><strong>Informação</strong> sobre com quem compartilhamos, <strong>oposição</strong> a um tratamento,
            <strong> anonimização ou bloqueio</strong> de dados desnecessários: peça pelo e-mail {mail}.</li>
        </List>
        <p>
          Respondemos pedidos por e-mail em até 15 dias. Podemos pedir uma confirmação de identidade antes de atender,
          para proteger sua conta. Se não ficar satisfeito, você pode reclamar à ANPD (gov.br/anpd).
        </p>
      </Section>

      <Section title="10. Idade mínima">
        <p>
          O LifePedia é destinado a maiores de 18 anos. Não coletamos de propósito dados de crianças ou adolescentes; se
          soubermos de uma conta assim, ela será excluída.
        </p>
      </Section>

      <Section title="11. Mudanças nesta política">
        <p>
          Quando esta política mudar de forma relevante, a nova versão aparece aqui com a data, e o app pede que você a
          leia e aceite antes de continuar.
        </p>
      </Section>

      <Section title="12. Contato">
        <p>Dúvidas ou pedidos sobre seus dados: {CONTROLLER.name}, {mail}.</p>
      </Section>
    </LegalLayout>
  )
}
