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
          <li>Finanças: ganhos e despesas fixas, lançamentos, categorias, banco de cada item (conta ou cartão), ajustes do
            mês, investimentos e os dados que você importa de extratos e faturas (inclusive a descrição original de cada
            lançamento).</li>
          <li>Tarefas, hábitos, agenda (inclusive as horas de sono que você informa), listas, etiquetas, lembretes e comentários.</li>
          <li>Frequência: compromissos que você cadastra (dias, horário, período, limite de faltas) e as presenças e faltas marcadas.</li>
          <li>Notas: o texto, as fotos que você coloca nelas, pastas, etiquetas, modelos e o histórico de quem criou,
            editou, arquivou ou mandou para a lixeira. Notas na lixeira são apagadas de vez depois de 30 dias.</li>
          <li>Financiamentos: simulações que você salva (tipo de bem, preço, entrada, FGTS, renda e prazo) e os dados dos
            contratos que você cadastra (valor, taxa, prazo, banco, amortizações extras e gastos do bem).</li>
          <li>Grupos: nome, imagem, membros, itens financeiros que você leva, planejamento (com links e fotos de produtos),
            financiamentos do grupo (com a parte de cada um), notas do grupo e mensagens do chat.</li>
          <li>Arquitetura: projetos (nome, descrição, latitude e direção do norte), as maquetes 3D que você envia (já
            convertidas e comprimidas no seu aparelho; o arquivo original do SketchUp não é enviado), a foto de capa, as
            versões, comentários presos na maquete, vistas salvas, variantes de acabamento (cores e texturas escolhidas),
            cômodos marcados (nome, área e contorno), luzes da vista noturna, anexos (imagens e PDFs), com quem você
            compartilhou e os links públicos que criou.</li>
          <li>Amigos, pedidos de amizade, conquistas e notificações.</li>
          <li>Preferências do menu: formato no computador e módulos que você escondeu.</li>
          <li>Contas externas que você liga (Google Agenda, GitLab, GitHub, Duolingo): o apelido e a cor que você deu, o
            usuário ou e-mail da conta, o endereço do GitLab da empresa, a validade do token e o histórico das
            sincronizações. Os tokens de acesso ficam <strong>cifrados</strong> e nunca são mostrados de novo.</li>
          <li>Dev: projetos de trabalho (nome, cor, descrição e atalhos), os repositórios ligados com as regras das raias
            e, copiados das suas contas, os títulos, números, etiquetas, autores e status dos MRs, issues e pipelines,
            sua atividade por dia (quantos commits, MRs e revisões) e os eventos do Google Agenda (título, horário, local
            e link da chamada) de 7 dias atrás a 60 dias à frente.</li>
          <li>Duolingo: ofensiva, maior ofensiva, XP e o dia da última lição, lidos do seu perfil público.</li>
        </List>
        <p><strong>Dados técnicos</strong></p>
        <List>
          <li>Sessões de login (identificador e validade), para manter você conectado e permitir sair de qualquer
            aparelho.</li>
          <li>Data e hora do seu último acesso (atualizada no máximo a cada 10 minutos), para saber quantas contas estão
            em uso e cuidar da segurança.</li>
          <li>Resultado de cada leitura pela importação do site (deu certo ou o motivo da falha, quantos arquivos e
            lançamentos e quanto tempo levou), sem o conteúdo dos arquivos.</li>
          <li>Endereço IP, usado na hora para limitar tentativas de login (proteção contra invasão) e registrado nos
            logs de acesso do servidor (IP, data e hora, página pedida e navegador).</li>
          <li>Se você ativar as notificações no aparelho: o endereço de entrega e as chaves fornecidas pelo navegador.</li>
        </List>
        <p>
          Não pedimos dados sensíveis (saúde, religião, biometria etc.). Evite escrevê-los em campos livres, como
          observações, notas e mensagens.
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
          <li><strong>Importação pelo site</strong>: só quando você envia os arquivos. Base: consentimento (art. 7º, I),
            dado a cada envio; basta não enviar, ou usar a importação com o seu agente.</li>
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
          <li><strong>Projetos de arquitetura</strong>: só quem você escolher. Amigos e membros dos grupos com quem você
            compartilha veem o projeto, a maquete, as versões, os anexos, os comentários (com nome e foto de quem
            escreveu) e quem enviou cada versão; conforme a permissão, também comentam ou editam. Um <strong>link
            público</strong> deixa qualquer pessoa que o receber ver, sem conta, a maquete atual, as vistas salvas, as variantes, os cômodos e as luzes dela, o nome
            e a descrição do projeto e o seu nome, até a data de validade ou até você apagar o link. Quem recebe pode
            repassar o link: compartilhe com cuidado.</li>
          <li><strong>Hospedagem</strong>: o provedor de servidor onde o LifePedia funciona armazena os dados em nosso
            nome, como operador.</li>
          <li><strong>Google</strong>: se você usar o botão do Google, a página carrega o script do Google e o Google
            sabe que você entrou no LifePedia, conforme a política de privacidade dele.</li>
          <li><strong>Google (Gemini)</strong>: se você usar a importação pelo site, os arquivos enviados vão inteiros,
            com tudo o que estiver neles, junto das suas categorias, fixos e nomes de lançamentos, para o Gemini ler e
            devolver os lançamentos. Usamos o plano pago da API, em que o Google não usa esse conteúdo para melhorar seus
            produtos, mas pode registrá-lo por tempo limitado para detectar abusos, conforme os termos dele.</li>
          <li><strong>Serviços que você liga</strong> (Google Agenda, GitLab, GitHub, Duolingo): o LifePedia acessa a sua
            conta neles só com a permissão que você deu e lê o que aparece no Dev, na Agenda e no Início. No Google, só a
            leitura das agendas; no GitLab e no GitHub, se o token permitir, o LifePedia também muda as etiquetas das
            issues (raias) e fecha issues quando você liga essas regras. No Duolingo lemos só o perfil público. Cada
            serviço trata os dados conforme a política dele. Desligar a conta apaga o que veio dela e, no Google, desfaz
            o acesso.</li>
          <li><strong>Google (Gemini) no resumo do daily</strong>: ao pedir o resumo no Dev, enviamos ao Gemini os títulos
            dos seus MRs e issues, os nomes dos repositórios, suas contagens de atividade do dia anterior e os títulos e
            horários das reuniões de hoje, nas mesmas condições da importação. Nada é enviado se você não pedir.</li>
          <li><strong>Serviços de notificação</strong> do seu navegador (Google, Mozilla, Apple ou Microsoft), se você
            ativar o push. O conteúdo vai <strong>criptografado de ponta a ponta</strong>: esses serviços só entregam,
            não conseguem ler.</li>
          <li><strong>Lojas e Buscapé</strong>: ao colar o link de um produto no planejamento, nosso servidor acessa a
            página da loja e, se ela não informar preço ou foto, busca o nome do produto no Buscapé. Nenhum dado seu é
            enviado além do link e do nome do produto.</li>
          <li><strong>Banco Central</strong>: nosso servidor baixa dos dados abertos do Banco Central as taxas médias dos
            bancos e os índices de correção (TR, IPCA, INCC) usados nos financiamentos. Nenhum dado seu é enviado.</li>
          <li><strong>Administradores do LifePedia</strong>: pessoas da equipe com acesso ao painel de administração
            veem seu nome, @, e-mail, foto, datas de cadastro e de último acesso, a versão dos termos aceita, se a conta
            usa Google ou notificações e <strong>quantos</strong> itens você tem em cada parte do app. Nunca veem o
            conteúdo (valores, descrições, tarefas, notas ou mensagens). Podem bloquear uma conta ou encerrar as sessões
            dela em caso de abuso ou pedido seu, e cada ação fica registrada.</li>
          <li><strong>Autoridades</strong>, somente quando a lei ou uma ordem judicial exigir.</li>
        </List>
        <p>
          <strong>Importação com IA</strong>: há dois caminhos, e você escolhe. <em>Pelo site</em>, o LifePedia envia os
          arquivos ao Google (Gemini): eles ficam no nosso servidor só enquanto a leitura roda e não são gravados; guardamos
          os lançamentos lidos como rascunho até você importar ou descartar (no máximo 30 dias sem mexer) e, por até 31
          dias, só a data do envio, para contar os limites. <em>Com o seu agente</em>, nada é enviado à IA pelo LifePedia:
          você copia o texto gerado e o leva, por conta própria, ao serviço que escolher, que trata os dados conforme as
          regras dele. Nesse caso oculte dados do cartão e documentos antes de enviar. Nos dois caminhos, o rascunho da
          revisão fica guardado da mesma forma.
        </p>
        <p>Não vendemos, alugamos ou cedemos seus dados a terceiros.</p>
      </Section>

      <Section title="5. Transferência internacional">
        <p>
          Google, GitLab, GitHub, Duolingo, os serviços de notificação dos navegadores e o provedor de hospedagem podem processar dados fora do
          Brasil. Nesses casos a transferência ocorre para prestar o serviço que você pediu (art. 33, V e IX), com
          fornecedores que adotam padrões de proteção compatíveis com a LGPD.
        </p>
      </Section>

      <Section title="6. Cookies e armazenamento no navegador">
        <List>
          <li><strong>Cookie de sessão</strong> (essencial): mantém você conectado. É protegido (HttpOnly, Secure,
            SameSite) e não pode ser lido por scripts.</li>
          <li><strong>Preferências de tela</strong> (tema, menu recolhido, dias recolhidos na semana, cores da agenda, ocultar valores,
            avisos do sistema que você fechou),
            guardadas só no seu navegador.</li>
        </List>
        <p>Não usamos cookies de publicidade, de análise ou de terceiros, por isso não pedimos consentimento para eles.</p>
      </Section>

      <Section title="7. Por quanto tempo guardamos">
        <List>
          <li>Seus dados ficam enquanto sua conta existir. Você pode apagar itens a qualquer momento.</li>
          <li><strong>Ao excluir a conta</strong>, apagamos na hora seus dados, fotos, projetos e maquetes, mensagens do chat, sessões e
            inscrições de notificação. Em cada grupo, seus itens saem como se você tivesse saído do grupo.</li>
          <li>Contas externas ficam até você desligá-las; o que veio delas (eventos, MRs, issues, pipelines, atividade) é
            refeito a cada sincronização e sai junto quando você desliga a conta. Eventos fora da janela de 7 dias atrás a
            60 à frente são apagados. Tarefas criadas a partir de issues continuam até você apagar.</li>
          <li>Projetos de arquitetura ficam até você excluí-los (maquetes, anexos e capa saem do servidor junto). Envios de
            maquete que não terminaram são descartados em até 2 horas, e links públicos vencidos são apagados depois de 30
            dias.</li>
          <li>Sessões vencidas são apagadas todos os dias. Ao sair da conta, o aparelho para de receber notificações.</li>
          <li>Logs de acesso do servidor são mantidos só pelo tempo necessário à segurança ou exigido por lei.</li>
          <li>O registro das ações dos administradores guarda o seu nome na época, se a ação foi sobre a sua conta.</li>
          <li>Notificações já entregues a outras pessoas guardam o nome que você tinha na época (por exemplo, um convite
            antigo).</li>
        </List>
      </Section>

      <Section title="8. Como protegemos">
        <List>
          <li>Conexão criptografada (HTTPS) e senhas com hash BCrypt.</li>
          <li>Tokens das contas externas cifrados (AES-256-GCM) com uma chave que fica só no servidor, fora do banco.</li>
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
