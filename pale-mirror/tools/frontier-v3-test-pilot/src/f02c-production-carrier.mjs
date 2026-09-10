const CAUSE = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const SCENE = 'assault:development-settlement-assault';

/** Cheap admission check for the one native-only production carrier; it never launches Minecraft. */
export function assertF02cProductionCarrier(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== 'disposable_production_carrier_restart' || declaration?.server?.profile !== 'settlement-assault'
      || !Array.isArray(actions) || actions.length !== 6 || declaration?.restart?.mode !== 'graceful' || declaration.restart.afterAction !== 3) {
    throw new Error('F0.2C production carrier declaration has an invalid identity or restart boundary');
  }
  if (declaration?.setup?.some(action => action.type === 'visit' || action.type === 'command')
      || actions.some(action => action.type === 'command' || /force/i.test(action.type ?? ''))) {
    throw new Error('F0.2C production carrier must not manufacture demand or canonical state');
  }
  const [cold, demand, hot, load, postcondition, terminal] = actions;
  exact(cold, 'wait_until_diagnostic', 'aftermath', CAUSE, 'cold_cause_pending');
  if (cold.expect?.epoch !== 4 || cold.expect?.cellStatus !== 'PENDING' || cold.expect?.terminal !== false) {
    throw new Error('F0.2C production carrier lacks the exact pre-demand COLD cause');
  }
  if (demand?.type !== 'visit' || demand.causalMilestone !== 'ordinary_player_demand' || demand.dimension !== 'pale_mirror:frontier_graybox') {
    throw new Error('F0.2C production carrier lacks ordinary dedicated-dimension player demand');
  }
  exact(hot, 'wait_until_diagnostic', 'scene', SCENE, 'hot_strike_confirmed');
  if (hot.expect?.leaseStatus !== 'HOT' || hot.expect?.strikeStatus !== 'CONFIRMED' || hot.expect?.cause !== CAUSE || hot.expect?.epoch !== 4) {
    throw new Error('F0.2C production carrier lacks the fenced ordinary HOT strike');
  }
  if (load?.type !== 'visit' || load.causalMilestone !== 'aftermath_natural_load' || load.dimension !== 'pale_mirror:frontier_graybox') {
    throw new Error('F0.2C production carrier lacks natural aftermath availability');
  }
  if (postcondition?.type !== 'wait_until_block' || postcondition.causalMilestone !== 'minecraft_postcondition' || postcondition.block !== 'minecraft:air') {
    throw new Error('F0.2C production carrier lacks the real Minecraft postcondition');
  }
  exact(terminal, 'wait_until_diagnostic', 'aftermath', CAUSE, 'cold_aftermath_realized');
  if (terminal.expect?.epoch !== 4 || terminal.expect?.cellStatus !== 'REALIZED' || terminal.expect?.terminal !== true || terminal.expect?.nextStatus !== 'NONE') {
    throw new Error('F0.2C production carrier lacks the terminal recovered aftermath oracle');
  }
  return Object.freeze({ cause: CAUSE, scene: SCENE, restartAfterAction: declaration.restart.afterAction });
}

function exact(action, type, view, id, milestone) {
  if (action?.type !== type || action.view !== view || action.id !== id || action.causalMilestone !== milestone) {
    throw new Error(`F0.2C production carrier lacks ${milestone}`);
  }
}
