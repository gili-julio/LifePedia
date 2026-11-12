import { CalendarDaysIcon, GitMergeIcon, GitPullRequestIcon, LanguagesIcon, type LucideIcon } from 'lucide-react'
import type { IntegrationProvider } from './types'

export type ProviderMeta = {
  label: string
  icon: LucideIcon
  description: string
  /** O que a pessoa ganha ao ligar */
  benefits: string[]
  /** Passo a passo para conectar */
  steps: string[]
  note?: string
  /** Dá para ter mais de uma conta */
  multiple: boolean
}

/** Textos da central de integrações (ordem da tela). */
export const PROVIDERS: Record<IntegrationProvider, ProviderMeta> = {
  GOOGLE: {
    label: 'Google Agenda',
    icon: CalendarDaysIcon,
    description: 'Reuniões e eventos das suas agendas do Google.',
    benefits: [
      'Eventos de várias contas Gmail na Agenda, cada conta com sua cor',
      'Reuniões do dia no painel "Hoje no dev" e na página do projeto',
      'Link da chamada (Meet) a um toque',
    ],
    steps: [
      'Toque em "Continuar no Google".',
      'Escolha a conta (pessoal ou do trabalho).',
      'Marque a permissão de ver suas agendas e confirme.',
      'Pronto: os eventos aparecem em alguns segundos. Repita para ligar outra conta.',
    ],
    note: 'Só leitura: o LifePedia nunca cria nem muda eventos. Enquanto o Google não aprovar o app, a conexão pode pedir para reconectar a cada 7 dias.',
    multiple: true,
  },
  GITLAB: {
    label: 'GitLab',
    icon: GitMergeIcon,
    description: 'MRs, pipelines e issues dos seus projetos.',
    benefits: [
      'Seus MRs, as revisões pedidas e as pipelines num lugar só',
      'Issues atribuídas a você viram tarefas',
      'Raias automáticas: abriu o MR, a issue vai para "esperando MR"; aceitou, vai para "livre para testes"',
      'Aviso no celular quando a pipeline falha, o MR é aprovado ou ganha comentário',
    ],
    steps: [
      'No GitLab, abra seu avatar e vá em "Edit profile" e depois "Access tokens".',
      'Clique em "Add new token", dê um nome (ex.: LifePedia) e escolha a validade.',
      'Marque o escopo read_api. Para mover issues de raia e fechar issues, marque api no lugar.',
      'Crie, copie o token (começa com glpat-) e cole aqui. O GitLab não mostra ele de novo.',
      'GitLab da empresa? Informe o endereço (ex.: https://gitlab.empresa.com). Ele precisa abrir fora da VPN.',
    ],
    multiple: true,
  },
  GITHUB: {
    label: 'GitHub',
    icon: GitPullRequestIcon,
    description: 'Pull requests, checks e issues dos seus repositórios.',
    benefits: [
      'Seus PRs, as revisões pedidas e os checks num lugar só',
      'Issues atribuídas a você viram tarefas',
      'Raias por etiqueta quando o PR abre e quando é aceito',
    ],
    steps: [
      'No GitHub, vá em Settings, Developer settings, Personal access tokens e "Fine-grained tokens".',
      'Clique em "Generate new token", escolha a validade e os repositórios.',
      'Em permissões do repositório, dê leitura em Pull requests, Issues, Actions e Metadata. Para mover issues, Issues em leitura e escrita.',
      'Gere, copie e cole aqui.',
    ],
    multiple: true,
  },
  DUOLINGO: {
    label: 'Duolingo',
    icon: LanguagesIcon,
    description: 'Sua ofensiva e seu XP.',
    benefits: [
      'Ofensiva no Início, dizendo se a lição de hoje já foi feita',
      'XP e ofensiva entram nas conquistas e aparecem para os amigos',
    ],
    steps: [
      'No app do Duolingo, abra o seu perfil e veja o nome de usuário (logo abaixo do nome).',
      'Digite aqui só o usuário, sem @.',
      'O perfil precisa estar público (é o padrão do Duolingo).',
    ],
    note: 'O Duolingo não tem integração oficial: usamos o perfil público, que pode mudar ou parar sem aviso.',
    multiple: false,
  },
}

export const PROVIDER_ORDER: IntegrationProvider[] = ['GOOGLE', 'GITLAB', 'GITHUB', 'DUOLINGO']

/** Mensagem da volta do Google (`?erro=` na URL). */
export const GOOGLE_ERRORS: Record<string, string> = {
  google: 'Não deu para conectar o Google. Tente de novo.',
  'google-cancelado': 'Conexão com o Google cancelada.',
  'google-permissao': 'Marque a permissão de ver suas agendas para conectar.',
  'google-limite': 'Não deu para criar a conta: limite de contas ou o Google não mandou a permissão de acesso contínuo.',
}
