import React, { useEffect, useRef, useState } from 'react';
import {
    ReactFlow,
    Handle,
    Position,
    MarkerType
} from '@xyflow/react';
import dagre from '@dagrejs/dagre';
import '@xyflow/react/dist/style.css';
import { InferredMapping } from "../../model/Mapping";


interface Node {
    id: string;
    data: {
        label: string;
        chainRule: string;
    };
    position: {
        x: number;
        y: number;
    };
    type: string;
}

/**
 *  Note that edges do not have labels. ChainRule information is displayed on handles (connectors on nodes).
 */
interface Edge {
    id: string;
    source: string;
    target: string;
    type: string;
    animated: boolean;
    markerEnd: {
        type: MarkerType,
        width: number,
        height: number,
        color: string
    }
}

const edgeMarker  = {
    type: MarkerType.ArrowClosed,
    width: 20,
    height: 20,
    color: '#222'
}
const edgeType = 'straight';

interface GraphData {
    nodes: Node[];
    edges: Edge[];
}

const nodeHeight = 30;
const nodeWidth = 200

const layoutElements = (nodes: Node[], edges: Edge[], direction = 'BT') => {
    // Create a new dagre graph instance each time
    const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({
        rankdir: direction, // Direction: BT (bottom-top), TB, LR, RL
        nodesep: 50,        // Space between nodes
        ranksep: 100,       // Space between ranks
        marginx: 20,        // Horizontal margin
        marginy: 20         // Vertical margin
    });

    nodes.forEach((node) => {
        dagreGraph.setNode(node.id, { width: nodeWidth, height: nodeHeight });
    });

    edges.forEach((edge) => {
        dagreGraph.setEdge(edge.source, edge.target);
    });

    dagre.layout(dagreGraph);

    const newNodes = nodes.map((node) => {
        const nodeWithPosition = dagreGraph.node(node.id);
        const newNode = {
            ...node,
            position: {
                x: nodeWithPosition.x - nodeWithPosition.width / 2,
                y: nodeWithPosition.y - nodeWithPosition.height / 2,
            },
        };

        return newNode;
    });

    return { nodes: newNodes, edges };
};


function createMappingId(mapping: InferredMapping): string {
    const data = `${mapping.objectIri}|${mapping.predicateIri}|${mapping.subjectIri}|${mapping.mappingSetId}`;
    return data;
}


function buildGraphData(mapping: InferredMapping): GraphData {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    const visited = new Set<string>();

    function traverse(current: InferredMapping) {
        const nodeId = createMappingId(current);
        if (visited.has(nodeId)) return;
        visited.add(nodeId);

        const isAsserted = current.chainRuleApplications?.chainRule?.chainRuleName === "Asserted";
        nodes.push({
            id: nodeId,
            data: {
                label: [
                    current.subjectId ?? "",
                    current.predicateId ?? "",
                    current.objectId ?? ""
                ].join("\n"),
                chainRule: isAsserted ? '' : current.chainRuleApplications?.chainRule?.chainRuleAbbreviated ?? ''
            },
            position: {
                x: 0,
                y: 0,
            },
            type: isAsserted ? "asserted" : "inferred"
        });

        const premises = current.chainRuleApplications?.premises;
        if (premises && premises.length > 0) {
            premises.forEach(premise => {
                const premiseId = createMappingId(premise);
                traverse(premise);

                edges.push({
                    id: `${premiseId}-${nodeId}`,
                    source: premiseId,
                    target: nodeId,
                    type: edgeType,
                    animated: false,
                    markerEnd: edgeMarker
                });
            });
        }
    }
    traverse(mapping);
    return { nodes, edges };
}

function CustomNode({ data }) {
    return (
        <div className="custom-node" style={{ position: "relative" }}>
            <div style={{ position: "relative", height: 0 }}>
                <Handle type="source" position={Position.Top} />
            </div>
            <div>
                {data.label.split('\n').map((line, idx) => (
                    <div key={idx}>{line}</div>
                ))}
            </div>
            <div className="handle-label handle-label-bottom" >
                <Handle type="target" position={Position.Bottom}/>
                {data.chainRule && (
                    <div>{data.chainRule}</div>
                )}
            </div>
        </div>
    );
}

const nodeTypes = {
    "asserted": CustomNode,
    "inferred": CustomNode,
};

const InferredMappingGraph = ({ explanation }: { explanation: InferredMapping }) => {

    const graphData = buildGraphData(explanation);
    const layoutGraph = layoutElements(graphData.nodes, graphData.edges);
    const canvasHeight = '400px';

    return (
        <div style={{ width: "100%", height: canvasHeight }}>
            <h1 className="section-subheading">Explanation of inferred mapping</h1>
            {layoutGraph && (
                    <ReactFlow
                        nodes={layoutGraph.nodes}
                        edges={layoutGraph.edges}
                        nodeTypes={nodeTypes}
                        fitView
                        fitViewOptions={{
                            minZoom: 0.5,
                            maxZoom: 1.0
                        }}
                        panOnDrag={true}
                        panOnScroll={true}
                    >
                    </ReactFlow>
                )}
        </div>
    );
}

export default InferredMappingGraph;
